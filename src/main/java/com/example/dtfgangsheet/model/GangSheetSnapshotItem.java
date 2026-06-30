package com.example.dtfgangsheet.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GangSheetSnapshotItem(
        @JsonProperty("img")
        @JsonAlias("imageId")
        String img,
        double x,
        double y,
        double width,
        double height,
        double rotation,
        boolean flipH,
        boolean flipV,
        ItemCrop crop,
        Integer dpi
) {
}
