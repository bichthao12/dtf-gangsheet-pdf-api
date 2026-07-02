package com.example.dtfgangsheet.dto.common;

import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiResultCode {

    // Success
    PDF_CREATED           ("MGS_1",  "PDF created successfully"),
    GANG_SHEET_SAVED      ("MGS_30", "Gang sheet saved as draft"),
    CART_ITEM_ADDED       ("MGS_31", "Item added to cart"),
    GANG_SHEET_SAVED_TO_CART ("MGS_42", "Gang sheet saved and added to cart"),
    GANG_SHEETS_LISTED    ("MGS_32", "OK"),
    ORDER_SUBMITTED       ("MGS_33", "Order submitted successfully"),
    ORDERS_LISTED         ("MGS_36", "OK"),

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
    GANG_SHEET_NOT_FOUND     ("MGS_34", "Gang sheet not found"),
    GANG_SHEET_CONFIRMED     ("MGS_35", "Gang sheet is confirmed and cannot be modified"),
    CART_LINE_NOT_FOUND      ("MGS_37", "Cart line not found"),
    CART_EMPTY               ("MGS_38", "Cart is empty"),
    ORDER_NOT_FOUND          ("MGS_39", "Order not found"),
    GANG_SHEET_NOT_ADDABLE   ("MGS_41", "Confirmed gang sheet cannot be added to cart"),
    UNSUPPORTED_PRODUCT_TYPE ("MGS_43", "Product type is not supported"),

    // Server errors
    PDF_IO_ERROR     ("MGS_8", "I/O error while generating PDF"),
    INTERNAL_ERROR   ("MGS_9", "Unexpected error");

    private final String code;
    private final String message;
    public static Optional<ApiResultCode> fromCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        for (ApiResultCode value : values()) {
            if (value.code.equals(code)) return Optional.of(value);
        }
        return Optional.empty();
    }
}