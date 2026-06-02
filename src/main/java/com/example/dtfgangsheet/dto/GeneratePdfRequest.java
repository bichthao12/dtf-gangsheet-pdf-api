package com.example.dtfgangsheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record GeneratePdfRequest(

        String outputFileName,

        @Positive(message = "sheetWidth must be greater than 0")
        double sheetWidth,

        @Positive(message = "sheetHeight must be greater than 0")
        double sheetHeight,

        @NotEmpty(message = "items must not be empty")
        List<@Valid GangSheetItemRequest> items
) {
}