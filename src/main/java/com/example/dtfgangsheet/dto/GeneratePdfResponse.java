package com.example.dtfgangsheet.dto;

import java.util.List;

public record GeneratePdfResponse(
        String message,
        String fileName,
        String absolutePath,
        int itemCount,
        double sheetWidth,
        double sheetHeight,
        String unit,
        List<String> warnings
) {
}