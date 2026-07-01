package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.common.ApiResponse;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.dto.request.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.request.SaveAndAddToCartRequest;
import com.example.dtfgangsheet.dto.request.SaveGangSheetRequest;
import com.example.dtfgangsheet.dto.response.GangSheetBuilderConfigResponse;
import com.example.dtfgangsheet.dto.response.GangSheetDetailResponse;
import com.example.dtfgangsheet.dto.response.GangSheetSummaryResponse;
import com.example.dtfgangsheet.dto.response.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.response.PageResponse;
import com.example.dtfgangsheet.dto.response.SaveAndAddToCartResponse;
import com.example.dtfgangsheet.dto.response.SaveGangSheetResponse;
import com.example.dtfgangsheet.model.GangSheetStatus;
import com.example.dtfgangsheet.service.GangSheetPdfService;
import com.example.dtfgangsheet.service.GangSheetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/gang-sheets")
public class GangSheetController {

    private final GangSheetPdfService gangSheetPdfService;
    private final GangSheetService gangSheetService;

    public GangSheetController(GangSheetPdfService gangSheetPdfService,
                               GangSheetService gangSheetService) {
        this.gangSheetPdfService = gangSheetPdfService;
        this.gangSheetService = gangSheetService;
    }

    /**
     * Save draft only. Response has no {@code items}; use GET /api/gang-sheets/{id} to reload layout.
     */
    @PostMapping
    public ApiResponse<SaveGangSheetResponse> saveDraft(
            @Valid @RequestBody SaveGangSheetRequest request
    ) {
        return ApiResponse.success(
                ApiResultCode.GANG_SHEET_SAVED.getCode(),
                ApiResultCode.GANG_SHEET_SAVED.getMessage(),
                gangSheetService.saveDraft(request.resolvedDesignId(), request)
        );
    }

    /**
     * Save draft + cart. {@code quantity} optional (default 1); change qty via PATCH /api/cart/items.
     */
    @PostMapping("/cart")
    public ApiResponse<SaveAndAddToCartResponse> saveAndAddToCart(
            @Valid @RequestBody SaveAndAddToCartRequest request
    ) {
        return ApiResponse.success(
                ApiResultCode.GANG_SHEET_SAVED_TO_CART.getCode(),
                ApiResultCode.GANG_SHEET_SAVED_TO_CART.getMessage(),
                gangSheetService.saveAndAddToCart(request.resolvedDesignId(), request)
        );
    }

    @GetMapping("/config")
    public ApiResponse<GangSheetBuilderConfigResponse> getBuilderConfig() {
        return ApiResponse.success(
                ApiResultCode.GANG_SHEETS_LISTED.getCode(),
                ApiResultCode.GANG_SHEETS_LISTED.getMessage(),
                gangSheetService.getBuilderConfig()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<GangSheetDetailResponse> getById(@PathVariable String id) {
        return ApiResponse.success(
                ApiResultCode.GANG_SHEETS_LISTED.getCode(),
                ApiResultCode.GANG_SHEETS_LISTED.getMessage(),
                gangSheetService.getById(id)
        );
    }

    @GetMapping
    public ApiResponse<PageResponse<GangSheetSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.success(
                ApiResultCode.GANG_SHEETS_LISTED.getCode(),
                ApiResultCode.GANG_SHEETS_LISTED.getMessage(),
                gangSheetService.list(GangSheetStatus.fromJson(status), page, size)
        );
    }

    @PostMapping("/pdf")
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
