package com.example.dtfgangsheet.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveGangSheetRequest(
        @JsonProperty("design_id")
        String designId,

        String name,

        @NotNull(message = "sheet must not be null")
        @Valid
        SheetSpecRequest sheet,

        @NotEmpty(message = "items must not be empty")
        List<@NotNull @Valid GangSheetItemRequest> items
) {

    /** {@code null} when creating a new gang sheet. */
    public String resolvedDesignId() {
        return designId != null && !designId.isBlank() ? designId.trim() : null;
    }

    public boolean isCreate() {
        return resolvedDesignId() == null;
    }
}
