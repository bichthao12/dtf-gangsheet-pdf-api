package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.GeneratePdfResponse;
import com.example.dtfgangsheet.service.GangSheetPdfService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/gang-sheets")
public class GangSheetController {

    private final GangSheetPdfService gangSheetPdfService;

    public GangSheetController(GangSheetPdfService gangSheetPdfService) {
        this.gangSheetPdfService = gangSheetPdfService;
    }

    @PostMapping("/pdf")
    public ResponseEntity<GeneratePdfResponse> generatePdf(
            @Valid @RequestBody List<@Valid GangSheetItemRequest> items
    ) throws IOException {

        GeneratePdfResponse response = gangSheetPdfService.generate(items);

        return ResponseEntity.ok(response);
    }
}