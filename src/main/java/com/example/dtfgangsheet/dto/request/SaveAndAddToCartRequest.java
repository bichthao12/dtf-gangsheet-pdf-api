package com.example.dtfgangsheet.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Save draft and ensure a cart line exists.
 * <p>{@code quantity} is optional — FE typically omits it (Ninja-style).
 * First add defaults to 1; re-save from cart edit keeps existing cart qty.
 * Change qty later via {@code PATCH /api/cart/items/{line_id}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveAndAddToCartRequest(
        @JsonProperty("design_id")
        String designId,

        String name,

        /** Optional. Omitted → first add uses 1; re-save keeps cart qty. */
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "sheet must not be null")
        @Valid
        SheetSpecRequest sheet,

        @NotEmpty(message = "items must not be empty")
        List<@NotNull @Valid GangSheetItemRequest> items
) {

    public String resolvedDesignId() {
        return designId != null && !designId.isBlank() ? designId.trim() : null;
    }

    public boolean isCreate() {
        return resolvedDesignId() == null;
    }

    public SaveGangSheetRequest toSaveRequest() {
        return new SaveGangSheetRequest(designId, name, sheet, items);
    }
}
