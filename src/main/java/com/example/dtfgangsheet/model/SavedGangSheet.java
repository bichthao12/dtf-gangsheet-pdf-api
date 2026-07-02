package com.example.dtfgangsheet.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;

public record SavedGangSheet(
        String id,
        String name,
        GangSheetStatus status,
        GangSheetSnapshot snapshot,
        String pdfId,
        Instant createdAt,
        Instant updatedAt,
        @JsonAlias("finalizedAt")
        Instant confirmedAt,
        boolean isDeleted,
        Instant deletedAt
) {

    @JsonIgnore
    public int itemCount() {
        List<GangSheetSnapshotItem> items = snapshot != null ? snapshot.items() : null;
        return items != null ? items.size() : 0;
    }

    @JsonIgnore
    public SheetSpec sheet() {
        return snapshot != null ? snapshot.sheet() : null;
    }

    @JsonIgnore
    public boolean isEditable() {
        return status == GangSheetStatus.DRAFT && !isDeleted;
    }
}
