package com.example.dtfgangsheet.model;

import java.util.List;

public record GangSheetSnapshot(
        SheetSpec sheet,
        List<GangSheetSnapshotItem> items
) {
}
