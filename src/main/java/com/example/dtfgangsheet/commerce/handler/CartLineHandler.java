package com.example.dtfgangsheet.commerce.handler;

import com.example.dtfgangsheet.dto.request.AddCartItemRequest;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.ProductType;

public interface CartLineHandler {

    ProductType productType();

    void validateAdd(AddCartItemRequest request);

    CartLine newCartLine(AddCartItemRequest request);

    /**
     * Ensures a line exists with exact quantity (set/replace). Used by Save &amp; Add to Cart.
     */
    CartLine ensureLine(CartLine existingOrNull, String referenceId, int quantity);

    String resolveDisplayName(CartLine line);
}
