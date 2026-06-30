package com.example.dtfgangsheet.entity;

import com.example.dtfgangsheet.model.GangSheetSnapshot;
import com.example.dtfgangsheet.model.GangSheetStatus;
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
@Table(name = "gang_sheets")
public class GangSheetEntity {

    @Id
    private String id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GangSheetStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    private GangSheetSnapshot snapshot;

    private String pdfId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant confirmedAt;

    protected GangSheetEntity() {
    }

    public GangSheetEntity(String id, String name, GangSheetStatus status, GangSheetSnapshot snapshot,
                           String pdfId, Instant createdAt, Instant updatedAt, Instant confirmedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.snapshot = snapshot;
        this.pdfId = pdfId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.confirmedAt = confirmedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public GangSheetStatus getStatus() { return status; }
    public GangSheetSnapshot getSnapshot() { return snapshot; }
    public String getPdfId() { return pdfId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
