package com.example.dtfgangsheet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(
        @Positive long maxBytes,
        @Positive long maxTotalBytesPerRequest,
        @Positive int maxItemsPerRequest,
        @Positive int renderDpi,
        @Positive long maxRasterPixels,
        @Positive int httpMaxAttempts,
        @PositiveOrZero long retryDelayMs,
        @PositiveOrZero long maxRetryDelayMs,
        @Positive long maxInputRasterPixels,
        @NotBlank String tempDir,
        @Positive int maxItemSizeInch,
        @Positive int maxPositionInch,
        @Positive int warningDpi,
        @PositiveOrZero int connectTimeoutMs,
        @PositiveOrZero int readTimeoutMs,
        @NotBlank String userAgent,
        @NotBlank String ffmpegPath,
        @NotBlank String ffprobePath
) {
}