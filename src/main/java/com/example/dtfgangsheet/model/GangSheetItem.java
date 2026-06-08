package com.example.dtfgangsheet.model;

/**
 * Item đã có tọa độ đầy đủ — dùng nội bộ giữa các service.
 *
 * Khác với {@link com.example.dtfgangsheet.dto.request.GangSheetItemRequest}:
 * class đó có annotation @Valid/@Positive dành cho HTTP request validation.
 * Class này không có annotation validation, dùng để truyền data giữa
 * MaxRectsNestingService → NestingService → GangSheetPdfService.
 */
public record GangSheetItem(
        String  img,
        double  x,
        double  y,
        double  width,
        double  height,
        double  rotation,
        Integer dpi
) {}