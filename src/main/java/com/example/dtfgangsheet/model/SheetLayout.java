package com.example.dtfgangsheet.model;

public record SheetLayout(double widthInch, double heightInch) {
    private static final double POINT_PER_INCH = 72.0;

    public float widthPt()  { return (float) (widthInch  * POINT_PER_INCH); }
    public float heightPt() { return (float) (heightInch * POINT_PER_INCH); }
}