package com.example.dtfgangsheet.entity;

import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.LinePayload;
import com.example.dtfgangsheet.model.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_lines")
public class OrderLineEntity {

    @Id
    @Column(name = "line_id", length = 36)
    private String lineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 40)
    private ProductType productType;

    @Column(name = "design_id", length = 36)
    private String referenceId;

    @Column(name = "design_name")
    private String designName;

    @Column(nullable = false)
    private int quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    private GangSheetSnapshot snapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    private LinePayload payload;

    protected OrderLineEntity() {
    }

    public OrderLineEntity(String lineId, ProductType productType, String referenceId, String designName,
                           int quantity, GangSheetSnapshot snapshot, LinePayload payload) {
        this.lineId = lineId;
        this.productType = productType != null ? productType : ProductType.DTF_GANG_SHEET_BUILDER;
        this.referenceId = referenceId;
        this.designName = designName;
        this.quantity = quantity;
        this.snapshot = snapshot;
        this.payload = payload;
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }

    public String getLineId() { return lineId; }
    public ProductType getProductType() { return productType; }
    public String getReferenceId() { return referenceId; }
    public String getDesignName() { return designName; }
    public int getQuantity() { return quantity; }
    public GangSheetSnapshot getSnapshot() { return snapshot; }
    public LinePayload getPayload() { return payload; }
}
