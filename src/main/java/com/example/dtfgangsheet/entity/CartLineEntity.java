package com.example.dtfgangsheet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "cart_lines")
public class CartLineEntity {

    @Id
    @Column(name = "line_id", length = 36)
    private String lineId;

    @Column(name = "design_id", nullable = false, length = 36)
    private String designId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private Instant addedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CartLineEntity() {
    }

    public CartLineEntity(String lineId, String designId, int quantity, Instant addedAt, Instant updatedAt) {
        this.lineId = lineId;
        this.designId = designId;
        this.quantity = quantity;
        this.addedAt = addedAt;
        this.updatedAt = updatedAt;
    }

    public String getLineId() { return lineId; }
    public String getDesignId() { return designId; }
    public int getQuantity() { return quantity; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
