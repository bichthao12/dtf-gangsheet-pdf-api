package com.example.dtfgangsheet.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GangSheetItemRequest(

        @NotBlank(message = "img must not be blank")
        String img,

        double x,

        double y,

        @Positive(message = "width must be greater than 0")
        double width,

        @Positive(message = "height must be greater than 0")
        double height,

        double rotation,

        Integer dpi
) {
}
