package com.example.dtfgangsheet.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddCartItemRequest(
        @NotBlank(message = "design_id must not be blank")
        @JsonProperty("design_id")
        String designId,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity
) {
}
