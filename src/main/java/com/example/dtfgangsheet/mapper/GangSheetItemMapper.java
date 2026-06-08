package com.example.dtfgangsheet.mapper;

import com.example.dtfgangsheet.dto.request.GangSheetItemRequest;
import com.example.dtfgangsheet.model.GangSheetItem;

import java.util.List;

public final class GangSheetItemMapper {

    private GangSheetItemMapper() {
    }

    public static GangSheetItem toModel(GangSheetItemRequest request) {
        if (request == null) {
            return null;
        }

        return new GangSheetItem(
                request.img(),
                request.x(),
                request.y(),
                request.width(),
                request.height(),
                request.rotation(),
                request.dpi()
        );
    }

    public static List<GangSheetItem> toModels(List<GangSheetItemRequest> requests) {
        if (requests == null) {
            return List.of();
        }

        return requests.stream()
                .map(GangSheetItemMapper::toModel)
                .toList();
    }
}