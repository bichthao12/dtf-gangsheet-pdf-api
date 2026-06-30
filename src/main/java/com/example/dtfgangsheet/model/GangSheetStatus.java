package com.example.dtfgangsheet.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GangSheetStatus {
    DRAFT,
    CONFIRMED;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static GangSheetStatus fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("FINALIZED".equals(normalized) || "LOCKED".equals(normalized)) {
            return CONFIRMED;
        }
        return valueOf(normalized);
    }
}
