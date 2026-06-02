package com.example.dtfgangsheet.dto;

public record ImageAsset(
        String source,
        byte[] bytes,
        int width,
        int height,
        ImageFormat format
) {

    public enum ImageFormat {
        JPEG,
        OTHER
    }
}
