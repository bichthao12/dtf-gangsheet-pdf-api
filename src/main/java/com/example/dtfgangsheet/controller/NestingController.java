package com.example.dtfgangsheet.controller;

import com.example.dtfgangsheet.dto.ApiResponse;
import com.example.dtfgangsheet.dto.ApiResultCode;
import com.example.dtfgangsheet.dto.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.NestingRequest;
import com.example.dtfgangsheet.service.NestingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated   // thêm dòng này
@RestController
@RequestMapping("/api/nesting")
public class NestingController {

    private final NestingService nestingService;

    public NestingController(NestingService nestingService) {
        this.nestingService = nestingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GeneratePdfResponse> nest(
            @Valid @RequestBody @NotEmpty List<@Valid NestingRequest> requests
    ) {
        return ApiResponse.success(
                ApiResultCode.PDF_CREATED.getCode(),
                ApiResultCode.PDF_CREATED.getMessage(),
                nestingService.nestAndGenerate(requests)
        );
    }
}