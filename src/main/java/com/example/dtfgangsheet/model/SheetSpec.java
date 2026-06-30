package com.example.dtfgangsheet.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SheetSpec(
        double widthInch,
        double heightInch,
        String unit
) {
    public static final String UNIT_INCH = "INCH";

    /** Builder layout — dùng khi save draft (chưa chừa QR). */
    public static SheetSpec fromLayout(SheetLayout layout) {
        return new SheetSpec(layout.widthInch(), layout.heightInch(), UNIT_INCH);
    }
}
