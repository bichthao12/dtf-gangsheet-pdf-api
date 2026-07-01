package com.example.dtfgangsheet.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Polymorphic JSON payload for cart / order lines (extensible per {@link ProductType}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LinePayload(Map<String, Object> data) {

    public LinePayload {
        data = data == null || data.isEmpty() ? null : Map.copyOf(data);
    }

    public static LinePayload empty() {
        return new LinePayload(null);
    }

    public static LinePayload of(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return empty();
        }
        return new LinePayload(values);
    }
}
