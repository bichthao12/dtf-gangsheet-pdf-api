package com.example.dtfgangsheet.commerce.handler;

import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.OrderLine;
import com.example.dtfgangsheet.model.ProductType;

import java.time.Instant;

public interface CheckoutLineHandler {

    ProductType productType();

    OrderLine toOrderLine(CartLine cartLine);

    void afterCheckout(CartLine cartLine, Instant now);
}
