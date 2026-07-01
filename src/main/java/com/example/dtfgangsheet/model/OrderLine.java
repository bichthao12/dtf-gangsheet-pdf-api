package com.example.dtfgangsheet.model;

public record OrderLine(
        String lineId,
        ProductType productType,
        String referenceId,
        String designName,
        int quantity,
        GangSheetSnapshot snapshot,
        LinePayload payload
) {

    /** Builder lines use {@code referenceId} as {@code design_id}. */
    public String designId() {
        return referenceId;
    }
}
