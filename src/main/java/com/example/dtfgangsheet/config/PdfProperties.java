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
        @PositiveOrZero double itemGapInch,
        @Positive double qrCodeSizeInch,
        @PositiveOrZero double qrCodeMarginInch
) {
    /**
     * bottomPadding thực tế dùng để render.
     * Nếu rightPadding đủ chứa QR → giữ nguyên bottomPadding.
     * Nếu không đủ → tự động tăng bottomPadding để chừa chỗ cho QR.
     */
    public double effectiveBottomPaddingInch() {
        double minQrSpace = qrCodeSizeInch + qrCodeMarginInch * 2;
        if (rightPaddingInch >= minQrSpace) {
            return bottomPaddingInch;
        }
        return Math.max(bottomPaddingInch, minQrSpace);
    }
}