package com.example.dtfgangsheet.model;

public record OrderLine(
        String lineId,
        String designId,
        String designName,
        int quantity,
        GangSheetSnapshot snapshot
) {
}
