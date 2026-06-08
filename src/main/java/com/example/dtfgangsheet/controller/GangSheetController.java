package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.ApiResponse;
import com.example.dtfgangsheet.dto.ApiResultCode;
import com.example.dtfgangsheet.dto.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.GeneratePdfResponse;
import com.example.dtfgangsheet.service.GangSheetPdfService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GeneratePdfResponse> generatePdf(
            @Valid @RequestBody List<@NotNull @Valid GangSheetItemRequest> items
    ) throws IOException {

        GeneratePdfResponse response = gangSheetPdfService.generate(items);

        return ApiResponse.success(
                ApiResultCode.PDF_CREATED.getCode(),
                ApiResultCode.PDF_CREATED.getMessage(),
                response
        );
    }
}
