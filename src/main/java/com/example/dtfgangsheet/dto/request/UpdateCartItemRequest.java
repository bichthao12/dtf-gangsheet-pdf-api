package com.example.dtfgangsheet.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateCartItemRequest(
        @Min(value = 1, message = "quantity must be at least 1")
        int quantity
) {
}
