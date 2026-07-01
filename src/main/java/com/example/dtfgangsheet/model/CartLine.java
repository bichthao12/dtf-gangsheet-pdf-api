package com.example.dtfgangsheet.model;

import java.time.Instant;

public record CartLine(
        String lineId,
        ProductType productType,
        String referenceId,
        int quantity,
        LinePayload payload,
        Instant addedAt,
        Instant updatedAt
) {

    /** Builder lines use {@code referenceId} as {@code design_id}. */
    public String designId() {
        return referenceId;
    }

    public static CartLine builderLine(
            String lineId,
            String designId,
            int quantity,
            Instant addedAt,
            Instant updatedAt
    ) {
        return new CartLine(
                lineId,
                ProductType.DTF_GANG_SHEET_BUILDER,
                designId,
                quantity,
                LinePayload.empty(),
                addedAt,
                updatedAt
        );
    }
}
