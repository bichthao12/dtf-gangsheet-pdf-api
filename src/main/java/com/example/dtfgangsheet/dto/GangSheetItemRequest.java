package com.example.dtfgangsheet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GangSheetItemRequest(

        @NotBlank(message = "img must not be blank")
        String img,

        @PositiveOrZero(message = "x must be greater than or equal to 0")
        double x,

        @PositiveOrZero(message = "y must be greater than or equal to 0")
        double y,

        @Positive(message = "width must be greater than 0")
        double width,

        @Positive(message = "height must be greater than 0")
        double height,

        double rotation,

        Integer dpi
) {
}