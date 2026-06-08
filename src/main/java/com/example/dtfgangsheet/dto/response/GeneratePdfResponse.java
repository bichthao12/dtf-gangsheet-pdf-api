package com.example.dtfgangsheet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeneratePdfResponse(
        String id,
        String fileName,
        String downloadUrl,
        int itemCount,
        PdfSheetResponse sheet,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> warnings
) {
}
