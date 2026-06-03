package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.ApiResponse;
import com.example.dtfgangsheet.dto.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.GeneratePdfResponse;
import com.example.dtfgangsheet.service.GangSheetPdfService;
import jakarta.validation.Valid;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/gang-sheets")
public class GangSheetController {

    private final GangSheetPdfService gangSheetPdfService;

    public GangSheetController(GangSheetPdfService gangSheetPdfService) {
        this.gangSheetPdfService = gangSheetPdfService;
    }

    @PostMapping("/pdf")
    public ResponseEntity<ApiResponse<GeneratePdfResponse>> generatePdf(
            @Valid @RequestBody List<@Valid GangSheetItemRequest> items
    ) throws IOException {

        GeneratePdfResponse response = gangSheetPdfService.generate(items);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "PDF_CREATED",
                        "PDF created successfully",
                        response
                ));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String id) throws IOException {
        Path pdfPath = gangSheetPdfService.resolveGeneratedPdf(id);
        Resource resource = new PathResource(pdfPath);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(resource.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(pdfPath.getFileName().toString())
                                .build()
                                .toString()
                )
                .body(resource);
    }
}
