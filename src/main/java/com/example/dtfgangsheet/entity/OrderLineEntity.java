package com.example.dtfgangsheet.entity;

import com.example.dtfgangsheet.model.GangSheetSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "design_id", nullable = false, length = 36)
    private String designId;

    @Column(name = "design_name")
    private String designName;

    @Column(nullable = false)
    private int quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    private GangSheetSnapshot snapshot;

    protected OrderLineEntity() {
    }

    public OrderLineEntity(String lineId, String designId, String designName,
                           int quantity, GangSheetSnapshot snapshot) {
        this.lineId = lineId;
        this.designId = designId;
        this.designName = designName;
        this.quantity = quantity;
        this.snapshot = snapshot;
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }

    public String getLineId() { return lineId; }
    public String getDesignId() { return designId; }
    public String getDesignName() { return designName; }
    public int getQuantity() { return quantity; }
    public GangSheetSnapshot getSnapshot() { return snapshot; }
}
