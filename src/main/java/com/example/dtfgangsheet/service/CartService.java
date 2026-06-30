package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.request.AddCartItemRequest;
import com.example.dtfgangsheet.dto.request.UpdateCartItemRequest;
import com.example.dtfgangsheet.dto.response.CartLineResponse;
import com.example.dtfgangsheet.dto.response.CartResponse;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.exception.AppException;
import com.example.dtfgangsheet.exception.CartLineNotFoundException;
import com.example.dtfgangsheet.exception.GangSheetNotFoundException;
import com.example.dtfgangsheet.mapper.CartMapper;
import com.example.dtfgangsheet.model.Cart;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.repository.CartRepository;
import com.example.dtfgangsheet.repository.GangSheetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final GangSheetRepository gangSheetRepository;

    public CartService(CartRepository cartRepository, GangSheetRepository gangSheetRepository) {
        this.cartRepository = cartRepository;
        this.gangSheetRepository = gangSheetRepository;
    }

    public CartResponse getCart() {
        return toResponse(cartRepository.load());
    }

    public CartResponse addItem(AddCartItemRequest request) {
        requireAddableDesign(request.designId());
        Cart cart = cartRepository.load();
        Instant now = Instant.now();

        List<CartLine> lines = new ArrayList<>(cart.lines());
        int existingIndex = indexOfDesign(lines, request.designId());

        if (existingIndex >= 0) {
            CartLine existing = lines.get(existingIndex);
            lines.set(existingIndex, new CartLine(
                    existing.lineId(),
                    existing.designId(),
                    existing.quantity() + request.quantity(),
                    existing.addedAt(),
                    now
            ));
        } else {
            lines.add(new CartLine(
                    UUID.randomUUID().toString(),
                    request.designId(),
                    request.quantity(),
                    now,
                    now
            ));
        }

        cartRepository.save(new Cart(lines, now));
        return toResponse(cartRepository.load());
    }

    /**
     * Ensures the design is in cart with the given quantity (set/replace, not merge).
     */
    public int ensureItem(String designId, int quantity) {
        requireAddableDesign(designId);
        Cart cart = cartRepository.load();
        Instant now = Instant.now();

        List<CartLine> lines = new ArrayList<>(cart.lines());
        int existingIndex = indexOfDesign(lines, designId);

        if (existingIndex >= 0) {
            CartLine existing = lines.get(existingIndex);
            lines.set(existingIndex, new CartLine(
                    existing.lineId(),
                    existing.designId(),
                    quantity,
                    existing.addedAt(),
                    now
            ));
        } else {
            lines.add(new CartLine(
                    UUID.randomUUID().toString(),
                    designId,
                    quantity,
                    now,
                    now
            ));
        }

        cartRepository.save(new Cart(lines, now));
        return quantity;
    }

    public CartResponse updateItem(String lineId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.load();
        Instant now = Instant.now();

        List<CartLine> lines = cart.lines().stream()
                .map(line -> line.lineId().equals(lineId)
                        ? new CartLine(line.lineId(), line.designId(), request.quantity(), line.addedAt(), now)
                        : line)
                .toList();

        if (lines.stream().noneMatch(line -> line.lineId().equals(lineId))) {
            throw new CartLineNotFoundException(lineId);
        }

        cartRepository.save(new Cart(lines, now));
        return toResponse(cartRepository.load());
    }

    public CartResponse removeItem(String lineId) {
        Cart cart = cartRepository.load();
        List<CartLine> lines = cart.lines().stream()
                .filter(line -> !line.lineId().equals(lineId))
                .toList();

        if (lines.size() == cart.lines().size()) {
            throw new CartLineNotFoundException(lineId);
        }

        cartRepository.save(new Cart(lines, Instant.now()));
        return toResponse(cartRepository.load());
    }

    public void clear() {
        cartRepository.clear();
    }

    public int quantityForDesign(String designId) {
        return cartRepository.load().lines().stream()
                .filter(line -> line.designId().equals(designId))
                .mapToInt(CartLine::quantity)
                .sum();
    }

    public boolean isInCart(String designId) {
        return quantityForDesign(designId) > 0;
    }

    public Cart loadCart() {
        return cartRepository.load();
    }

    private SavedGangSheet requireAddableDesign(String designId) {
        SavedGangSheet gangSheet = gangSheetRepository.findById(designId)
                .orElseThrow(() -> new GangSheetNotFoundException(designId));

        if (!gangSheet.isEditable()) {
            throw new AppException(
                    ApiResultCode.GANG_SHEET_NOT_ADDABLE,
                    HttpStatus.CONFLICT,
                    "Confirmed gang sheet cannot be added to cart: " + designId
            );
        }
        return gangSheet;
    }

    private CartResponse toResponse(Cart cart) {
        List<CartLineResponse> lines = cart.lines().stream()
                .map(line -> {
                    SavedGangSheet gangSheet = gangSheetRepository.findById(line.designId()).orElse(null);
                    return CartMapper.toLineResponse(line, gangSheet);
                })
                .toList();

        int totalItems = lines.stream().mapToInt(CartLineResponse::quantity).sum();
        return new CartResponse(lines, totalItems, cart.updatedAt());
    }

    private int indexOfDesign(List<CartLine> lines, String designId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).designId().equals(designId)) {
                return i;
            }
        }
        return -1;
    }
}
