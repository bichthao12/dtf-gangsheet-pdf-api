package com.example.dtfgangsheet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NestingResponse(

        List<GangSheetItemRequest> items,
        PdfSheetResponse sheet,
        NestingStats stats

) {
    public record NestingStats(
            int totalPlaced,
            int totalRequested,
            double usagePercent,
            double sheetHeightInch,
            int rotatedCount,

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            List<String> skipped
    ) {}
}