package com.example.dtfgangsheet.entity;

import com.example.dtfgangsheet.model.LinePayload;
import com.example.dtfgangsheet.model.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "cart_lines")
public class CartLineEntity {

    @Id
    @Column(name = "line_id", length = 36)
    private String lineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 40)
    private ProductType productType;

    @Column(name = "design_id", length = 36)
    private String referenceId;

    @Column(nullable = false)
    private int quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    private LinePayload payload;

    @Column(nullable = false)
    private Instant addedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CartLineEntity() {
    }

    public CartLineEntity(String lineId, ProductType productType, String referenceId, int quantity,
                          LinePayload payload, Instant addedAt, Instant updatedAt) {
        this.lineId = lineId;
        this.productType = productType != null ? productType : ProductType.DTF_GANG_SHEET_BUILDER;
        this.referenceId = referenceId;
        this.quantity = quantity;
        this.payload = payload;
        this.addedAt = addedAt;
        this.updatedAt = updatedAt;
    }

    public String getLineId() { return lineId; }
    public ProductType getProductType() { return productType; }
    public String getReferenceId() { return referenceId; }
    public int getQuantity() { return quantity; }
    public LinePayload getPayload() { return payload; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
