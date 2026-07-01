package com.example.dtfgangsheet.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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

        @JsonAlias("flipH")
        Boolean flipH,

        @JsonAlias("flipV")
        Boolean flipV,

        CropRequest crop,

        Integer dpi
) {
}
