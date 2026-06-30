package com.example.dtfgangsheet.dto.request;

import com.example.dtfgangsheet.model.SheetSpec;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SheetSpecRequest(
        @Positive(message = "sheet.width_inch must be greater than 0")
        double widthInch,

        @Positive(message = "sheet.height_inch must be greater than 0")
        double heightInch,

        String unit
) {

    public SheetSpec toSheetSpec(double configWidthInch) {
        String resolvedUnit = unit != null && !unit.isBlank() ? unit.trim() : SheetSpec.UNIT_INCH;
        return new SheetSpec(configWidthInch, heightInch, resolvedUnit);
    }
}
