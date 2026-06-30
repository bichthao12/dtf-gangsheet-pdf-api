package com.example.dtfgangsheet.model;

import java.time.Instant;
import java.util.List;

public record Cart(
        List<CartLine> lines,
        Instant updatedAt
) {
    public static Cart empty() {
        return new Cart(List.of(), Instant.now());
    }
}
