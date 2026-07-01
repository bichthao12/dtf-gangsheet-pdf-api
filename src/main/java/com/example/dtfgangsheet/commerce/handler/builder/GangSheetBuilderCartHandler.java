package com.example.dtfgangsheet.commerce.handler.builder;

import com.example.dtfgangsheet.commerce.handler.CartLineHandler;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.dto.request.AddCartItemRequest;
import com.example.dtfgangsheet.exception.AppException;
import com.example.dtfgangsheet.exception.GangSheetNotFoundException;
import com.example.dtfgangsheet.mapper.GangSheetSnapshotMapper;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.LinePayload;
import com.example.dtfgangsheet.model.ProductType;
import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.repository.GangSheetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Component
public class GangSheetBuilderCartHandler implements CartLineHandler {

    private final GangSheetRepository gangSheetRepository;

    public GangSheetBuilderCartHandler(GangSheetRepository gangSheetRepository) {
        this.gangSheetRepository = gangSheetRepository;
    }

    @Override
    public ProductType productType() {
        return ProductType.DTF_GANG_SHEET_BUILDER;
    }

    @Override
    public void validateAdd(AddCartItemRequest request) {
        requireAddableDesign(requireReferenceId(request));
    }

    @Override
    public CartLine newCartLine(AddCartItemRequest request) {
        String designId = requireReferenceId(request);
        requireAddableDesign(designId);
        Instant now = Instant.now();
        return CartLine.builderLine(
                UUID.randomUUID().toString(),
                designId,
                request.quantity(),
                now,
                now
        );
    }

    @Override
    public CartLine ensureLine(CartLine existingOrNull, String referenceId, int quantity) {
        requireAddableDesign(referenceId);
        Instant now = Instant.now();
        if (existingOrNull != null) {
            return new CartLine(
                    existingOrNull.lineId(),
                    ProductType.DTF_GANG_SHEET_BUILDER,
                    referenceId,
                    quantity,
                    existingOrNull.payload(),
                    existingOrNull.addedAt(),
                    now
            );
        }
        return CartLine.builderLine(
                UUID.randomUUID().toString(),
                referenceId,
                quantity,
                now,
                now
        );
    }

    @Override
    public String resolveDisplayName(CartLine line) {
        if (!StringUtils.hasText(line.referenceId())) {
            return GangSheetSnapshotMapper.DEFAULT_NAME;
        }
        return gangSheetRepository.findById(line.referenceId())
                .map(sheet -> GangSheetSnapshotMapper.displayName(sheet.name()))
                .orElse(GangSheetSnapshotMapper.DEFAULT_NAME);
    }

    private String requireReferenceId(AddCartItemRequest request) {
        String referenceId = request.resolvedReferenceId();
        if (!StringUtils.hasText(referenceId)) {
            throw new AppException(
                    ApiResultCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "reference_id or design_id is required for DTF_GANG_SHEET_BUILDER"
            );
        }
        return referenceId.trim();
    }

    private SavedGangSheet requireAddableDesign(String designId) {
        SavedGangSheet gangSheet = gangSheetRepository.findById(designId)
                .orElseThrow(() -> new GangSheetNotFoundException(designId));

        if (!gangSheet.isEditable()) {
            throw new AppException(
                    ApiResultCode.GANG_SHEET_NOT_ADDABLE,
                    HttpStatus.CONFLICT,
                    "Confirmed gang sheet cannot be added to cart: " + designId
            );
        }
        return gangSheet;
    }
}
