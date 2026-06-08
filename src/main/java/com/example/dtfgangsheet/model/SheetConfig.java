package com.example.dtfgangsheet.model;

// model/SheetConfig.java — domain config, không phụ thuộc Spring
public record SheetConfig(
        double widthInch,
        double usableWidthInch,
        double bottomPaddingInch,
        double itemGapInch,
        int renderDpi
) {}