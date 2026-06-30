package com.example.dtfgangsheet.mapper;

import com.example.dtfgangsheet.dto.response.CreateOrderResponse;
import com.example.dtfgangsheet.dto.response.OrderDetailResponse;
import com.example.dtfgangsheet.dto.response.OrderSummaryResponse;
import com.example.dtfgangsheet.model.Order;
import com.example.dtfgangsheet.model.OrderLine;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static CreateOrderResponse toCreateResponse(Order order) {
        return new CreateOrderResponse(
                order.id(),
                order.status(),
                order.lines().size(),
                totalQuantity(order),
                order.submittedAt()
        );
    }

    public static OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.id(),
                order.status(),
                order.lines().size(),
                totalQuantity(order),
                order.submittedAt(),
                order.updatedAt()
        );
    }

    public static OrderDetailResponse toDetail(Order order) {
        return new OrderDetailResponse(
                order.id(),
                order.status(),
                order.lines(),
                totalQuantity(order),
                order.submittedAt(),
                order.updatedAt()
        );
    }

    private static int totalQuantity(Order order) {
        return order.lines().stream().mapToInt(OrderLine::quantity).sum();
    }
}
