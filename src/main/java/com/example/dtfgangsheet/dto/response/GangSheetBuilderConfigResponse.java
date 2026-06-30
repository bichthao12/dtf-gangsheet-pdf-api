package com.example.dtfgangsheet.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GangSheetBuilderConfigResponse(
        double sheetWidthInch,
        double rightPaddingInch,
        double bottomPaddingInch,
        String unit
) {
}
