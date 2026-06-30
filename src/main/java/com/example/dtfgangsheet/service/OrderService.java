package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.response.CreateOrderResponse;
import com.example.dtfgangsheet.dto.response.OrderDetailResponse;
import com.example.dtfgangsheet.dto.response.OrderSummaryResponse;
import com.example.dtfgangsheet.dto.response.PageResponse;
import com.example.dtfgangsheet.exception.CartEmptyException;
import com.example.dtfgangsheet.exception.GangSheetNotFoundException;
import com.example.dtfgangsheet.exception.OrderNotFoundException;
import com.example.dtfgangsheet.mapper.GangSheetSnapshotMapper;
import com.example.dtfgangsheet.mapper.OrderMapper;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.GangSheetStatus;
import com.example.dtfgangsheet.model.Order;
import com.example.dtfgangsheet.model.OrderLine;
import com.example.dtfgangsheet.model.OrderStatus;
import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.repository.GangSheetRepository;
import com.example.dtfgangsheet.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final GangSheetRepository gangSheetRepository;
    private final CartService cartService;

    public OrderService(OrderRepository orderRepository,
                        GangSheetRepository gangSheetRepository,
                        CartService cartService) {
        this.orderRepository = orderRepository;
        this.gangSheetRepository = gangSheetRepository;
        this.cartService = cartService;
    }

    public CreateOrderResponse checkout() {
        List<CartLine> cartLines = cartService.loadCart().lines();
        if (cartLines.isEmpty()) {
            throw new CartEmptyException();
        }

        Instant now = Instant.now();
        List<OrderLine> orderLines = new ArrayList<>();

        for (CartLine cartLine : cartLines) {
            SavedGangSheet gangSheet = gangSheetRepository.findById(cartLine.designId())
                    .orElseThrow(() -> new GangSheetNotFoundException(cartLine.designId()));

            if (gangSheet.snapshot() == null || gangSheet.snapshot().items() == null
                    || gangSheet.snapshot().items().isEmpty()) {
                throw new GangSheetNotFoundException(cartLine.designId());
            }

            orderLines.add(new OrderLine(
                    UUID.randomUUID().toString(),
                    gangSheet.id(),
                    GangSheetSnapshotMapper.displayName(gangSheet.name()),
                    cartLine.quantity(),
                    copySnapshot(gangSheet.snapshot())
            ));

            confirmGangSheet(gangSheet, now);
        }

        Order order = new Order(
                UUID.randomUUID().toString(),
                OrderStatus.SUBMITTED,
                orderLines,
                now,
                now
        );

        orderRepository.save(order);
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

    private void confirmGangSheet(SavedGangSheet gangSheet, Instant now) {
        if (gangSheet.status() == GangSheetStatus.CONFIRMED) {
            return;
        }

        SavedGangSheet confirmed = new SavedGangSheet(
                gangSheet.id(),
                gangSheet.name(),
                GangSheetStatus.CONFIRMED,
                gangSheet.snapshot(),
                gangSheet.pdfId(),
                gangSheet.createdAt(),
                now,
                now
        );
        gangSheetRepository.save(confirmed);
    }

    private GangSheetSnapshot copySnapshot(GangSheetSnapshot snapshot) {
        return new GangSheetSnapshot(
                snapshot.sheet(),
                snapshot.items() != null ? List.copyOf(snapshot.items()) : List.of()
        );
    }
}
