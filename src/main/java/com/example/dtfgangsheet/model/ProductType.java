package com.example.dtfgangsheet.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ZE product line discriminator for cart / order lines.
 * MVP implements {@link #DTF_GANG_SHEET_BUILDER}; others reserved for future modules.
 */
public enum ProductType {
    DTF_GANG_SHEET_BUILDER,
    DTF_GANG_SHEET_UPLOAD,
    DTF_BY_SIZE;

    @JsonValue
    public String json() {
        return name();
    }

    @JsonCreator
    public static ProductType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
