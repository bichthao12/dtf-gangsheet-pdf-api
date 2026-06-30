package com.example.dtfgangsheet.entity;

import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant submittedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineEntity> lines = new ArrayList<>();

    protected OrderEntity() {
    }

    public OrderEntity(String id, OrderStatus status, Instant submittedAt, Instant updatedAt) {
        this.id = id;
        this.status = status;
        this.submittedAt = submittedAt;
        this.updatedAt = updatedAt;
    }

    public void addLine(OrderLineEntity line) {
        lines.add(line);
        line.setOrder(this);
    }

    public String getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderLineEntity> getLines() { return lines; }
}
