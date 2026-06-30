package com.example.dtfgangsheet.model;

import java.time.Instant;
import java.util.List;

public record Order(
        String id,
        OrderStatus status,
        List<OrderLine> lines,
        Instant submittedAt,
        Instant updatedAt
) {
}
