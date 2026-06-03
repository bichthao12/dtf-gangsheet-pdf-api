package com.example.dtfgangsheet.dto;

import java.nio.file.Path;

public record LoadedFile(
        Path path,
        long sizeBytes,
        boolean temporary
) {
}
