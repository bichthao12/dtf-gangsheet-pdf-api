package com.example.dtfgangsheet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.example.dtfgangsheet.model.GangSheetStatus;
import com.example.dtfgangsheet.model.SheetSpec;

import java.time.Instant;

/**
 * Save + cart summary. Does <strong>not</strong> include {@code items} — use
 * {@code GET /api/gang-sheets/{design_id}} to restore full canvas layout.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SaveAndAddToCartResponse(
        @JsonProperty("design_id")
        String designId,
        String name,
        GangSheetStatus status,
        int itemCount,
        SheetSpec sheet,
        boolean inCart,
        int cartQuantity,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        Instant updatedAt
) {
}
