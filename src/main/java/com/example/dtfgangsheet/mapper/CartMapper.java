package com.example.dtfgangsheet.mapper;

import com.example.dtfgangsheet.dto.response.CartLineResponse;
import com.example.dtfgangsheet.dto.response.CartResponse;
import com.example.dtfgangsheet.model.Cart;
import com.example.dtfgangsheet.model.CartLine;

public final class CartMapper {

    private CartMapper() {
    }

    public static CartResponse toResponse(Cart cart) {
        int totalItems = cart.lines().stream().mapToInt(CartLine::quantity).sum();
        return new CartResponse(
                cart.lines().stream().map(line -> toLineResponse(line, null)).toList(),
                totalItems,
                cart.updatedAt()
        );
    }

    public static CartLineResponse toLineResponse(CartLine line, String displayName) {
        return new CartLineResponse(
                line.lineId(),
                line.productType(),
                line.referenceId(),
                line.designId(),
                displayName,
                line.quantity(),
                line.addedAt(),
                line.updatedAt()
        );
    }
}
