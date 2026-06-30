package com.example.dtfgangsheet.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CropRequest(
        double x,
        double y,
        @Positive(message = "crop.w must be greater than 0")
        double w,
        @Positive(message = "crop.h must be greater than 0")
        double h
) {
}
