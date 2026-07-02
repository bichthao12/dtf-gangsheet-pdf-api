package com.example.dtfgangsheet.commerce.handler.builder;

import com.example.dtfgangsheet.commerce.handler.CheckoutLineHandler;
import com.example.dtfgangsheet.commerce.payload.BuilderLinePayloadMapper;
import com.example.dtfgangsheet.exception.GangSheetNotFoundException;
import com.example.dtfgangsheet.mapper.GangSheetSnapshotMapper;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.GangSheetStatus;
import com.example.dtfgangsheet.model.LinePayload;
import com.example.dtfgangsheet.model.OrderLine;
import com.example.dtfgangsheet.model.ProductType;
import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.repository.GangSheetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class GangSheetBuilderCheckoutHandler implements CheckoutLineHandler {

    private final GangSheetRepository gangSheetRepository;
    private final ObjectMapper objectMapper;

    public GangSheetBuilderCheckoutHandler(GangSheetRepository gangSheetRepository, ObjectMapper objectMapper) {
        this.gangSheetRepository = gangSheetRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProductType productType() {
        return ProductType.DTF_GANG_SHEET_BUILDER;
    }

    @Override
    public OrderLine toOrderLine(CartLine cartLine) {
        SavedGangSheet gangSheet = gangSheetRepository.findById(cartLine.referenceId())
                .orElseThrow(() -> new GangSheetNotFoundException(cartLine.referenceId()));

        if (gangSheet.snapshot() == null || gangSheet.snapshot().items() == null
                || gangSheet.snapshot().items().isEmpty()) {
            throw new GangSheetNotFoundException(cartLine.referenceId());
        }

        GangSheetSnapshot frozen = copySnapshot(gangSheet.snapshot());
        LinePayload payload = BuilderLinePayloadMapper.orderPayload(
                gangSheet.id(), frozen, objectMapper);

        return new OrderLine(
                UUID.randomUUID().toString(),
                ProductType.DTF_GANG_SHEET_BUILDER,
                gangSheet.id(),
                GangSheetSnapshotMapper.displayName(gangSheet.name()),
                cartLine.quantity(),
                frozen,
                payload
        );
    }

    @Override
    public void afterCheckout(CartLine cartLine, Instant now) {
        SavedGangSheet gangSheet = gangSheetRepository.findById(cartLine.referenceId())
                .orElseThrow(() -> new GangSheetNotFoundException(cartLine.referenceId()));

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
                now,
                gangSheet.isDeleted(),
                gangSheet.deletedAt()
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
