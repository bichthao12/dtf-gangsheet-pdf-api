package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.commerce.handler.CheckoutLineHandler;
import com.example.dtfgangsheet.commerce.handler.ProductLineHandlerRegistry;
import com.example.dtfgangsheet.dto.response.CreateOrderResponse;
import com.example.dtfgangsheet.dto.response.OrderDetailResponse;
import com.example.dtfgangsheet.dto.response.OrderSummaryResponse;
import com.example.dtfgangsheet.dto.response.PageResponse;
import com.example.dtfgangsheet.exception.CartEmptyException;
import com.example.dtfgangsheet.exception.OrderNotFoundException;
import com.example.dtfgangsheet.mapper.OrderMapper;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.Order;
import com.example.dtfgangsheet.model.OrderLine;
import com.example.dtfgangsheet.model.OrderStatus;
import com.example.dtfgangsheet.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductLineHandlerRegistry handlerRegistry;

    public OrderService(OrderRepository orderRepository,
                        CartService cartService,
                        ProductLineHandlerRegistry handlerRegistry) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.handlerRegistry = handlerRegistry;
    }

    public CreateOrderResponse checkout() {
        List<CartLine> cartLines = cartService.loadCart().lines();
        if (cartLines.isEmpty()) {
            throw new CartEmptyException();
        }

        Instant now = Instant.now();
        List<OrderLine> orderLines = new ArrayList<>();

        for (CartLine cartLine : cartLines) {
            CheckoutLineHandler handler = handlerRegistry.checkoutHandler(cartLine.productType());
            orderLines.add(handler.toOrderLine(cartLine));
        }

        Order order = new Order(
                UUID.randomUUID().toString(),
                OrderStatus.SUBMITTED,
                orderLines,
                now,
                now
        );

        orderRepository.save(order);

        for (CartLine cartLine : cartLines) {
            handlerRegistry.checkoutHandler(cartLine.productType()).afterCheckout(cartLine, now);
        }

        cartService.clear();
        return OrderMapper.toCreateResponse(order);
    }

    public OrderDetailResponse getById(String id) {
        return orderRepository.findById(id)
                .map(OrderMapper::toDetail)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public PageResponse<OrderSummaryResponse> list(OrderStatus status, int page, int size) {
        List<Order> orders = orderRepository.findAll().stream()
                .filter(order -> status == null || order.status() == status)
                .toList();

        long totalElements = orders.size();
        int fromIndex = Math.min(page * size, orders.size());
        int toIndex = Math.min(fromIndex + size, orders.size());

        List<OrderSummaryResponse> content = orders.subList(fromIndex, toIndex).stream()
                .map(OrderMapper::toSummary)
                .toList();

        return new PageResponse<>(content, page, size, totalElements);
    }
}
