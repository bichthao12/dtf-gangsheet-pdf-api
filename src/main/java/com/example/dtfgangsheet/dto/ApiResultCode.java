package com.example.dtfgangsheet.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiResultCode {

    // Success
    PDF_CREATED      ("MGS_1", "PDF created successfully"),

    // Client errors
    BAD_REQUEST      ("MGS_2", "Invalid request"),
    VALIDATION_ERROR ("MGS_3", "Validation failed"),
    IMAGE_LOAD_ERROR ("MGS_4", "Cannot load image"),
    PDF_NOT_FOUND    ("MGS_5", "PDF not found"),
    INVALID_JSON     ("MGS_6", "Malformed request body"),
    PDF_GENERATION_FAILED     ("MGS_7", "Failed to generate pdf"),
    IMAGE_SIZE_EXCEEDED ("MGS_10", "Image size exceeds limit"),
    IMAGE_NOT_FOUND("MGS_11", "Image not found"),
    UNSUPPORTED_IMAGE_FORMAT("MGS_12", "Unsupported image format"),
    IMAGE_FETCH_ERROR("MGS_13", "Cannot load image from URL"),
    INVALID_GANG_SHEET_LAYOUT("MGS_14", "Invalid gang sheet layout"),

    // Server errors
    PDF_IO_ERROR     ("MGS_7", "I/O error while generating PDF"),
    INTERNAL_ERROR   ("MGS_8", "Unexpected error");

    private final String code;
    private final String message;
}