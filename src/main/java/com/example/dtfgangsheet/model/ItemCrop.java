package com.example.dtfgangsheet.model;

public record ItemCrop(
        double x,
        double y,
        double w,
        double h
) {
    public static ItemCrop fullFrame() {
        return new ItemCrop(0, 0, 1, 1);
    }
}
