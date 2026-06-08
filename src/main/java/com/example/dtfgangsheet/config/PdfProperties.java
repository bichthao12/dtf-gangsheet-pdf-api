package com.example.dtfgangsheet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.pdf")
public record PdfProperties(
        @NotBlank String outputDir,
        @Positive double sheetWidthInch,
        @PositiveOrZero double rightPaddingInch,
        @PositiveOrZero double bottomPaddingInch,
        @PositiveOrZero double itemGapInch

) { }
