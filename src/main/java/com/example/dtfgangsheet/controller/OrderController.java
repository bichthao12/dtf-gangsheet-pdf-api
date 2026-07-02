package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.common.ApiResponse;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.dto.response.CreateOrderResponse;
import com.example.dtfgangsheet.dto.response.OrderDetailResponse;
import com.example.dtfgangsheet.dto.response.OrderSummaryResponse;
import com.example.dtfgangsheet.dto.response.PageResponse;
import com.example.dtfgangsheet.model.OrderStatus;
import com.example.dtfgangsheet.service.OrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Checkout — chốt gang sheet từ cart, tạo order */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateOrderResponse> checkout() {
        return ApiResponse.success(
                ApiResultCode.ORDER_SUBMITTED.getCode(),
                ApiResultCode.ORDER_SUBMITTED.getMessage(),
                orderService.checkout(),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getById(@PathVariable String id) {
        return ApiResponse.success(
                ApiResultCode.ORDERS_LISTED.getCode(),
                ApiResultCode.ORDERS_LISTED.getMessage(),
                orderService.getById(id)
        );
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(
                ApiResultCode.ORDERS_LISTED.getCode(),
                ApiResultCode.ORDERS_LISTED.getMessage(),
                orderService.list(OrderStatus.fromJson(status), page, size)
        );
    }
}
