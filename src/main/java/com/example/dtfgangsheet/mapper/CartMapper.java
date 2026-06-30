package com.example.dtfgangsheet.mapper;

import com.example.dtfgangsheet.dto.response.CartLineResponse;
import com.example.dtfgangsheet.dto.response.CartResponse;
import com.example.dtfgangsheet.dto.response.CreateOrderResponse;
import com.example.dtfgangsheet.dto.response.OrderDetailResponse;
import com.example.dtfgangsheet.dto.response.OrderSummaryResponse;
import com.example.dtfgangsheet.model.Cart;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.Order;
import com.example.dtfgangsheet.model.SavedGangSheet;

public final class CartMapper {

    private CartMapper() {
    }

    public static CartResponse toResponse(Cart cart) {
        int totalItems = cart.lines().stream().mapToInt(CartLine::quantity).sum();
        return new CartResponse(
                cart.lines().stream().map(CartMapper::toLineResponse).toList(),
                totalItems,
                cart.updatedAt()
        );
    }

    public static CartLineResponse toLineResponse(CartLine line, SavedGangSheet gangSheet) {
        String name = gangSheet != null ? GangSheetSnapshotMapper.displayName(gangSheet.name()) : null;
        return new CartLineResponse(
                line.lineId(),
                line.designId(),
                name,
                line.quantity(),
                line.addedAt(),
                line.updatedAt()
        );
    }

    private static CartLineResponse toLineResponse(CartLine line) {
        return new CartLineResponse(
                line.lineId(),
                line.designId(),
                null,
                line.quantity(),
                line.addedAt(),
                line.updatedAt()
        );
    }
}
