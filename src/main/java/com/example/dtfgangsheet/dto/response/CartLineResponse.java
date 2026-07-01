package com.example.dtfgangsheet.dto.response;

import com.example.dtfgangsheet.model.ProductType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CartLineResponse(
        @JsonProperty("line_id")
        String lineId,
        ProductType productType,
        @JsonProperty("reference_id")
        String referenceId,
        /** Builder alias; same value as {@code reference_id} for {@link ProductType#DTF_GANG_SHEET_BUILDER}. */
        @JsonProperty("design_id")
        String designId,
        String name,
        int quantity,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        Instant addedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        Instant updatedAt
) {
}
