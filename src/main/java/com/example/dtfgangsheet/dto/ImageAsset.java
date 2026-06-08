package com.example.dtfgangsheet.dto;

import java.nio.file.Path;

public record ImageAsset(
        String source,
        Path path,
        long sizeBytes,
        int width,
        int height,
        ImageFormat format,
        boolean temporary
) {

    public enum ImageFormat {
        PNG,
        GIF,
        WEBP,
        AVIF,
        OTHER
    }
}