package com.example.dtfgangsheet.mapper;

import com.example.dtfgangsheet.dto.request.CropRequest;
import com.example.dtfgangsheet.dto.request.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.response.GangSheetDetailResponse;
import com.example.dtfgangsheet.dto.response.GangSheetSummaryResponse;
import com.example.dtfgangsheet.dto.response.SaveAndAddToCartResponse;
import com.example.dtfgangsheet.dto.response.SaveGangSheetResponse;
import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.GangSheetSnapshotItem;
import com.example.dtfgangsheet.model.ItemCrop;
import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.model.SheetSpec;

import java.util.List;

public final class GangSheetSnapshotMapper {

    public static final String DEFAULT_NAME = "New Gang Sheet";

    private GangSheetSnapshotMapper() {
    }

    public static GangSheetSnapshot fromRequest(SheetSpec sheet, List<GangSheetItemRequest> items) {
        return new GangSheetSnapshot(
                sheet,
                items.stream()
                        .map(GangSheetSnapshotMapper::toSnapshotItem)
                        .toList()
        );
    }

    public static SaveGangSheetResponse toSaveResponse(SavedGangSheet gangSheet) {
        return new SaveGangSheetResponse(
                gangSheet.id(),
                displayName(gangSheet.name()),
                gangSheet.status(),
                gangSheet.itemCount(),
                gangSheet.sheet(),
                gangSheet.createdAt(),
                gangSheet.updatedAt()
        );
    }

    public static SaveAndAddToCartResponse toSaveAndAddToCartResponse(
            SavedGangSheet gangSheet,
            int cartQuantity
    ) {
        return new SaveAndAddToCartResponse(
                gangSheet.id(),
                displayName(gangSheet.name()),
                gangSheet.status(),
                gangSheet.itemCount(),
                gangSheet.sheet(),
                cartQuantity > 0,
                cartQuantity,
                gangSheet.createdAt(),
                gangSheet.updatedAt()
        );
    }

    public static GangSheetSummaryResponse toSummary(
            SavedGangSheet gangSheet,
            boolean inCart,
            int cartQuantity
    ) {
        return new GangSheetSummaryResponse(
                gangSheet.id(),
                displayName(gangSheet.name()),
                gangSheet.status(),
                gangSheet.itemCount(),
                inCart,
                cartQuantity,
                gangSheet.createdAt(),
                gangSheet.updatedAt(),
                gangSheet.confirmedAt()
        );
    }

    public static GangSheetDetailResponse toDetail(
            SavedGangSheet gangSheet,
            boolean inCart,
            int cartQuantity
    ) {
        List<GangSheetSnapshotItem> items = gangSheet.snapshot() != null
                ? gangSheet.snapshot().items()
                : List.of();

        return new GangSheetDetailResponse(
                gangSheet.id(),
                displayName(gangSheet.name()),
                gangSheet.status(),
                gangSheet.itemCount(),
                gangSheet.sheet(),
                items,
                inCart,
                cartQuantity,
                gangSheet.createdAt(),
                gangSheet.updatedAt(),
                gangSheet.confirmedAt()
        );
    }

    public static String displayName(String name) {
        return name != null && !name.isBlank() ? name : DEFAULT_NAME;
    }

    private static GangSheetSnapshotItem toSnapshotItem(GangSheetItemRequest request) {
        return new GangSheetSnapshotItem(
                request.img(),
                request.x(),
                request.y(),
                request.width(),
                request.height(),
                request.rotation(),
                Boolean.TRUE.equals(request.flipH()),
                Boolean.TRUE.equals(request.flipV()),
                toCrop(request.crop()),
                request.dpi()
        );
    }

    private static ItemCrop toCrop(CropRequest crop) {
        if (crop == null) {
            return ItemCrop.fullFrame();
        }
        return new ItemCrop(crop.x(), crop.y(), crop.w(), crop.h());
    }
}
