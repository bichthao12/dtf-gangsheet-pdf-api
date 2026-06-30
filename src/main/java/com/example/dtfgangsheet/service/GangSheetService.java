package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.PdfProperties;
import com.example.dtfgangsheet.dto.request.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.request.SaveAndAddToCartRequest;
import com.example.dtfgangsheet.dto.request.SaveGangSheetRequest;
import com.example.dtfgangsheet.dto.request.SheetSpecRequest;
import com.example.dtfgangsheet.dto.response.GangSheetBuilderConfigResponse;
import com.example.dtfgangsheet.dto.response.GangSheetDetailResponse;
import com.example.dtfgangsheet.dto.response.GangSheetSummaryResponse;
import com.example.dtfgangsheet.dto.response.PageResponse;
import com.example.dtfgangsheet.dto.response.SaveAndAddToCartResponse;
import com.example.dtfgangsheet.dto.response.SaveGangSheetResponse;
import com.example.dtfgangsheet.exception.GangSheetConfirmedException;
import com.example.dtfgangsheet.exception.GangSheetNotFoundException;
import com.example.dtfgangsheet.mapper.GangSheetSnapshotMapper;
import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.GangSheetStatus;
import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.repository.GangSheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class GangSheetService {

    private final GangSheetRepository gangSheetRepository;
    private final CartService cartService;
    private final PdfLayoutService layoutService;
    private final double sheetWidthInch;

    public GangSheetService(GangSheetRepository gangSheetRepository,
                            CartService cartService,
                            PdfLayoutService layoutService,
                            PdfProperties pdfProperties) {
        this.gangSheetRepository = gangSheetRepository;
        this.cartService = cartService;
        this.layoutService = layoutService;
        this.sheetWidthInch = pdfProperties.sheetWidthInch();
    }

    /** @param id {@code null} to create, non-null to update an existing draft */
    public SaveGangSheetResponse saveDraft(String id, SaveGangSheetRequest request) {
        return GangSheetSnapshotMapper.toSaveResponse(persistDraft(id, request));
    }

    /** @param id {@code null} to create, non-null to update an existing draft */
    @Transactional
    public SaveAndAddToCartResponse saveAndAddToCart(String id, SaveAndAddToCartRequest request) {
        SavedGangSheet gangSheet = persistDraft(id, request.toSaveRequest());
        int cartQuantity = cartService.ensureItem(gangSheet.id(), request.resolvedQuantity());
        return GangSheetSnapshotMapper.toSaveAndAddToCartResponse(gangSheet, cartQuantity);
    }

    public GangSheetDetailResponse getById(String id) {
        SavedGangSheet gangSheet = gangSheetRepository.findById(id)
                .orElseThrow(() -> new GangSheetNotFoundException(id));

        int cartQuantity = cartService.quantityForDesign(id);
        return GangSheetSnapshotMapper.toDetail(gangSheet, cartQuantity > 0, cartQuantity);
    }

    public PageResponse<GangSheetSummaryResponse> list(GangSheetStatus status, int page, int size) {
        List<SavedGangSheet> sheets = gangSheetRepository.findAll().stream()
                .filter(sheet -> status == null || sheet.status() == status)
                .toList();

        long totalElements = sheets.size();
        int fromIndex = Math.min(page * size, sheets.size());
        int toIndex = Math.min(fromIndex + size, sheets.size());

        List<GangSheetSummaryResponse> content = sheets.subList(fromIndex, toIndex).stream()
                .map(gangSheet -> GangSheetSnapshotMapper.toSummary(
                        gangSheet,
                        cartService.isInCart(gangSheet.id()),
                        cartService.quantityForDesign(gangSheet.id())))
                .toList();

        return new PageResponse<>(content, page, size, totalElements);
    }

    public GangSheetBuilderConfigResponse getBuilderConfig() {
        return layoutService.builderConfig();
    }

    private SavedGangSheet persistDraft(String id, SaveGangSheetRequest request) {
        Instant now = Instant.now();
        GangSheetSnapshot snapshot = snapshotFromRequest(request.sheet(), request.items());

        SavedGangSheet gangSheet;
        if (id == null) {
            gangSheet = new SavedGangSheet(
                    UUID.randomUUID().toString(),
                    resolveName(request.name()),
                    GangSheetStatus.DRAFT,
                    snapshot,
                    null,
                    now,
                    now,
                    null);
        } else {
            SavedGangSheet existing = requireEditable(id);
            String resolvedName = StringUtils.hasText(request.name()) ? request.name().trim() : existing.name();
            gangSheet = new SavedGangSheet(
                    existing.id(),
                    resolvedName,
                    GangSheetStatus.DRAFT,
                    snapshot,
                    existing.pdfId(),
                    existing.createdAt(),
                    now,
                    existing.confirmedAt());
        }

        gangSheetRepository.save(gangSheet);
        return gangSheet;
    }

    private GangSheetSnapshot snapshotFromRequest(SheetSpecRequest sheet, List<GangSheetItemRequest> items) {
        return GangSheetSnapshotMapper.fromRequest(sheet.toSheetSpec(sheetWidthInch), items);
    }

    private SavedGangSheet requireEditable(String id) {
        SavedGangSheet gangSheet = gangSheetRepository.findById(id)
                .orElseThrow(() -> new GangSheetNotFoundException(id));

        if (!gangSheet.isEditable()) {
            throw new GangSheetConfirmedException(id);
        }
        return gangSheet;
    }

    private String resolveName(String name) {
        return StringUtils.hasText(name) ? name.trim() : GangSheetSnapshotMapper.DEFAULT_NAME;
    }
}
