package com.example.dtfgangsheet.commerce.handler;

import com.example.dtfgangsheet.exception.UnsupportedProductTypeException;
import com.example.dtfgangsheet.model.ProductType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductLineHandlerRegistry {

    private final Map<ProductType, CartLineHandler> cartHandlers;
    private final Map<ProductType, CheckoutLineHandler> checkoutHandlers;

    public ProductLineHandlerRegistry(List<CartLineHandler> cartHandlers, List<CheckoutLineHandler> checkoutHandlers) {
        this.cartHandlers = new EnumMap<>(ProductType.class);
        for (CartLineHandler handler : cartHandlers) {
            this.cartHandlers.put(handler.productType(), handler);
        }
        this.checkoutHandlers = new EnumMap<>(ProductType.class);
        for (CheckoutLineHandler handler : checkoutHandlers) {
            this.checkoutHandlers.put(handler.productType(), handler);
        }
    }

    public CartLineHandler cartHandler(ProductType productType) {
        CartLineHandler handler = cartHandlers.get(productType);
        if (handler == null) {
            throw new UnsupportedProductTypeException(productType);
        }
        return handler;
    }

    public CheckoutLineHandler checkoutHandler(ProductType productType) {
        CheckoutLineHandler handler = checkoutHandlers.get(productType);
        if (handler == null) {
            throw new UnsupportedProductTypeException(productType);
        }
        return handler;
    }
}
