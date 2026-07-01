package com.example.dtfgangsheet.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GangSheetSnapshotItem(
        @JsonProperty("img")
        @JsonAlias("imageId")
        String img,
        double x,
        double y,
        double width,
        double height,
        double rotation,
        @JsonAlias("flipH")
        boolean flipH,
        @JsonAlias("flipV")
        boolean flipV,
        ItemCrop crop,
        Integer dpi
) {
}
