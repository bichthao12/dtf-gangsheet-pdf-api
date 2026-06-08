package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.common.ApiResponse;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.dto.request.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.response.GeneratePdfResponse;
import com.example.dtfgangsheet.service.GangSheetPdfService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
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
    ) {
        return ApiResponse.success(
                ApiResultCode.PDF_CREATED.getCode(),
                ApiResultCode.PDF_CREATED.getMessage(),
                gangSheetPdfService.generate(items)
        );
    }
}