package com.example.dtfgangsheet.persistence;

import com.example.dtfgangsheet.entity.CartLineEntity;
import com.example.dtfgangsheet.entity.GangSheetEntity;
import com.example.dtfgangsheet.entity.OrderEntity;
import com.example.dtfgangsheet.entity.OrderLineEntity;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.model.LinePayload;
import com.example.dtfgangsheet.model.Order;
import com.example.dtfgangsheet.model.OrderLine;
import com.example.dtfgangsheet.model.ProductType;
import com.example.dtfgangsheet.model.SavedGangSheet;

public final class PersistenceMapper {

    private PersistenceMapper() {
    }

    public static SavedGangSheet toGangSheet(GangSheetEntity entity) {
        return new SavedGangSheet(
                entity.getId(),
                entity.getName(),
                entity.getStatus(),
                entity.getSnapshot(),
                entity.getPdfId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getConfirmedAt(),
                entity.isDeleted(),
                entity.getDeletedAt()
        );
    }

    public static GangSheetEntity toGangSheetEntity(SavedGangSheet gangSheet) {
        return new GangSheetEntity(
                gangSheet.id(),
                gangSheet.name(),
                gangSheet.status(),
                gangSheet.snapshot(),
                gangSheet.pdfId(),
                gangSheet.createdAt(),
                gangSheet.updatedAt(),
                gangSheet.confirmedAt(),
                gangSheet.isDeleted(),
                gangSheet.deletedAt()
        );
    }

    public static CartLine toCartLine(CartLineEntity entity) {
        return new CartLine(
                entity.getLineId(),
                entity.getProductType() != null ? entity.getProductType() : ProductType.DTF_GANG_SHEET_BUILDER,
                entity.getReferenceId(),
                entity.getQuantity(),
                entity.getPayload() != null ? entity.getPayload() : LinePayload.empty(),
                entity.getAddedAt(),
                entity.getUpdatedAt()
        );
    }

    public static CartLineEntity toCartLineEntity(CartLine line) {
        return new CartLineEntity(
                line.lineId(),
                line.productType(),
                line.referenceId(),
                line.quantity(),
                line.payload(),
                line.addedAt(),
                line.updatedAt()
        );
    }

    public static Order toOrder(OrderEntity entity) {
        return new Order(
                entity.getId(),
                entity.getStatus(),
                entity.getLines().stream().map(PersistenceMapper::toOrderLine).toList(),
                entity.getSubmittedAt(),
                entity.getUpdatedAt()
        );
    }

    public static OrderEntity toOrderEntity(Order order) {
        OrderEntity entity = new OrderEntity(
                order.id(),
                order.status(),
                order.submittedAt(),
                order.updatedAt()
        );
        for (OrderLine line : order.lines()) {
            entity.addLine(toOrderLineEntity(line));
        }
        return entity;
    }

    private static OrderLine toOrderLine(OrderLineEntity entity) {
        return new OrderLine(
                entity.getLineId(),
                entity.getProductType() != null ? entity.getProductType() : ProductType.DTF_GANG_SHEET_BUILDER,
                entity.getReferenceId(),
                entity.getDesignName(),
                entity.getQuantity(),
                entity.getSnapshot(),
                entity.getPayload()
        );
    }

    private static OrderLineEntity toOrderLineEntity(OrderLine line) {
        return new OrderLineEntity(
                line.lineId(),
                line.productType(),
                line.referenceId(),
                line.designName(),
                line.quantity(),
                line.snapshot(),
                line.payload()
        );
    }
}
