package com.example.dtfgangsheet.model;

import java.time.Instant;

public record CartLine(
        String lineId,
        String designId,
        int quantity,
        Instant addedAt,
        Instant updatedAt
) {
}
