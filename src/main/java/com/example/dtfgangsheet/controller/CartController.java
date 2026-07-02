package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.common.ApiResponse;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.dto.request.AddCartItemRequest;
import com.example.dtfgangsheet.dto.request.UpdateCartItemRequest;
import com.example.dtfgangsheet.dto.response.CartResponse;
import com.example.dtfgangsheet.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        return ApiResponse.success(
                ApiResultCode.GANG_SHEETS_LISTED.getCode(),
                ApiResultCode.GANG_SHEETS_LISTED.getMessage(),
                cartService.getCart()
        );
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.success(
                ApiResultCode.CART_ITEM_ADDED.getCode(),
                ApiResultCode.CART_ITEM_ADDED.getMessage(),
                cartService.addItem(request),
                HttpStatus.CREATED
        );
    }

    @PatchMapping("/items/{lineId}")
    public ApiResponse<CartResponse> updateItem(
            @PathVariable String lineId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return ApiResponse.success(
                ApiResultCode.CART_ITEM_ADDED.getCode(),
                ApiResultCode.CART_ITEM_ADDED.getMessage(),
                cartService.updateItem(lineId, request)
        );
    }

    @DeleteMapping("/items/{lineId}")
    public ApiResponse<CartResponse> removeItem(@PathVariable String lineId) {
        return ApiResponse.success(
                ApiResultCode.GANG_SHEETS_LISTED.getCode(),
                ApiResultCode.GANG_SHEETS_LISTED.getMessage(),
                cartService.removeItem(lineId)
        );
    }
}
