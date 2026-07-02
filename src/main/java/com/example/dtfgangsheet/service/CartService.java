package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.commerce.handler.CartLineHandler;
import com.example.dtfgangsheet.commerce.handler.ProductLineHandlerRegistry;
import com.example.dtfgangsheet.dto.request.AddCartItemRequest;
import com.example.dtfgangsheet.dto.request.UpdateCartItemRequest;
import com.example.dtfgangsheet.dto.response.CartLineResponse;
import com.example.dtfgangsheet.dto.response.CartResponse;
import com.example.dtfgangsheet.exception.CartLineNotFoundException;
import com.example.dtfgangsheet.mapper.CartMapper;
import com.example.dtfgangsheet.model.Cart;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.ProductType;
import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.repository.CartRepository;
import com.example.dtfgangsheet.repository.GangSheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductLineHandlerRegistry handlerRegistry;
    private final GangSheetRepository gangSheetRepository;

    public CartService(CartRepository cartRepository,
                       ProductLineHandlerRegistry handlerRegistry,
                       GangSheetRepository gangSheetRepository) {
        this.cartRepository = cartRepository;
        this.handlerRegistry = handlerRegistry;
        this.gangSheetRepository = gangSheetRepository;
    }

    public CartResponse getCart() {
        return toResponse(cartRepository.load());
    }

    public CartResponse addItem(AddCartItemRequest request) {
        ProductType productType = request.resolvedProductType();
        CartLineHandler handler = handlerRegistry.cartHandler(productType);
        handler.validateAdd(request);

        Cart cart = cartRepository.load();
        Instant now = Instant.now();
        List<CartLine> lines = new ArrayList<>(cart.lines());
        String referenceId = request.resolvedReferenceId();
        int existingIndex = indexOfReference(lines, productType, referenceId);

        if (existingIndex >= 0) {
            CartLine existing = lines.get(existingIndex);
            lines.set(existingIndex, new CartLine(
                    existing.lineId(),
                    productType,
                    referenceId,
                    existing.quantity() + request.quantity(),
                    existing.payload(),
                    existing.addedAt(),
                    now
            ));
        } else {
            lines.add(handler.newCartLine(request));
        }

        cartRepository.save(new Cart(lines, now));
        return toResponse(cartRepository.load());
    }

    /**
     * Resolves cart qty for {@link GangSheetService#saveAndAddToCart}.
     * Explicit {@code requestedQuantity} wins; otherwise keep existing line qty or default 1.
     */
    public int resolveEnsureQuantity(String designId, Integer requestedQuantity) {
        if (requestedQuantity != null) {
            return requestedQuantity;
        }
        int existing = quantityForDesign(designId);
        return existing > 0 ? existing : 1;
    }

    /** Builder convenience: ensures gang sheet design is in cart with exact quantity. */
    public int ensureItem(String designId, int quantity) {
        return ensureItem(ProductType.DTF_GANG_SHEET_BUILDER, designId, quantity);
    }

    public int ensureItem(ProductType productType, String referenceId, int quantity) {
        CartLineHandler handler = handlerRegistry.cartHandler(productType);
        Cart cart = cartRepository.load();
        Instant now = Instant.now();

        List<CartLine> lines = new ArrayList<>(cart.lines());
        int existingIndex = indexOfReference(lines, productType, referenceId);
        CartLine existing = existingIndex >= 0 ? lines.get(existingIndex) : null;
        CartLine updated = handler.ensureLine(existing, referenceId, quantity);

        if (existingIndex >= 0) {
            lines.set(existingIndex, updated);
        } else {
            lines.add(updated);
        }

        cartRepository.save(new Cart(lines, now));
        return quantity;
    }

    public CartResponse updateItem(String lineId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.load();
        Instant now = Instant.now();

        List<CartLine> lines = cart.lines().stream()
                .map(line -> line.lineId().equals(lineId)
                        ? new CartLine(
                                line.lineId(),
                                line.productType(),
                                line.referenceId(),
                                request.quantity(),
                                line.payload(),
                                line.addedAt(),
                                now)
                        : line)
                .toList();

        if (lines.stream().noneMatch(line -> line.lineId().equals(lineId))) {
            throw new CartLineNotFoundException(lineId);
        }

        cartRepository.save(new Cart(lines, now));
        return toResponse(cartRepository.load());
    }

    /**
     * Removes cart line. For {@link ProductType#DTF_GANG_SHEET_BUILDER}, soft-deletes the
     * linked draft design ({@code is_deleted=true}, {@code deleted_at}) — user must create a new gang sheet to order again.
     */
    @Transactional
    public CartResponse removeItem(String lineId) {
        Cart cart = cartRepository.load();
        CartLine removed = cart.lines().stream()
                .filter(line -> line.lineId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new CartLineNotFoundException(lineId));

        List<CartLine> lines = cart.lines().stream()
                .filter(line -> !line.lineId().equals(lineId))
                .toList();

        cartRepository.save(new Cart(lines, Instant.now()));
        deleteBuilderDesignIfDraft(removed);

        return toResponse(cartRepository.load());
    }

    public void clear() {
        cartRepository.clear();
    }

    public int quantityForDesign(String designId) {
        return cartRepository.load().lines().stream()
                .filter(line -> line.productType() == ProductType.DTF_GANG_SHEET_BUILDER)
                .filter(line -> designId.equals(line.referenceId()))
                .mapToInt(CartLine::quantity)
                .sum();
    }

    public boolean isInCart(String designId) {
        return quantityForDesign(designId) > 0;
    }

    public Cart loadCart() {
        return cartRepository.load();
    }

    private CartResponse toResponse(Cart cart) {
        List<CartLineResponse> lines = cart.lines().stream()
                .map(line -> {
                    String name = handlerRegistry.cartHandler(line.productType()).resolveDisplayName(line);
                    return CartMapper.toLineResponse(line, name);
                })
                .toList();

        int totalItems = lines.stream().mapToInt(CartLineResponse::quantity).sum();
        return new CartResponse(lines, totalItems, cart.updatedAt());
    }

    private int indexOfReference(List<CartLine> lines, ProductType productType, String referenceId) {
        for (int i = 0; i < lines.size(); i++) {
            CartLine line = lines.get(i);
            if (line.productType() == productType && referenceId.equals(line.referenceId())) {
                return i;
            }
        }
        return -1;
    }

    private void deleteBuilderDesignIfDraft(CartLine removedLine) {
        if (removedLine.productType() != ProductType.DTF_GANG_SHEET_BUILDER) {
            return;
        }
        String designId = removedLine.referenceId();
        if (designId == null || designId.isBlank()) {
            return;
        }
        gangSheetRepository.findById(designId)
                .filter(SavedGangSheet::isEditable)
                .ifPresent(sheet -> gangSheetRepository.softDeleteById(sheet.id()));
    }
}
