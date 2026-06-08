package com.example.dtfgangsheet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NestingRequest(

        @NotBlank(message = "img must not be blank")
        String img,

        @Positive(message = "quantity must be >= 1")
        int quantity,

        @Positive(message = "width must be > 0")
        double width,

        @Positive(message = "height must be > 0")
        double height

) {}