package com.example.dtfgangsheet.dto.request;

import com.example.dtfgangsheet.model.LinePayload;
import com.example.dtfgangsheet.model.ProductType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddCartItemRequest(
        @JsonProperty("product_type")
        ProductType productType,

        @JsonProperty("reference_id")
        String referenceId,

        /** Backward compatible alias for builder {@code reference_id}. */
        @JsonProperty("design_id")
        String designId,

        LinePayload payload,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity
) {

    public ProductType resolvedProductType() {
        return productType != null ? productType : ProductType.DTF_GANG_SHEET_BUILDER;
    }

    public String resolvedReferenceId() {
        if (referenceId != null && !referenceId.isBlank()) {
            return referenceId.trim();
        }
        if (designId != null && !designId.isBlank()) {
            return designId.trim();
        }
        return null;
    }
}
