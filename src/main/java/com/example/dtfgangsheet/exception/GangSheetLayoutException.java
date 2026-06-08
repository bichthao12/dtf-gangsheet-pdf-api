package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiErrorDetail;
import com.example.dtfgangsheet.dto.ApiResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class GangSheetLayoutException extends RuntimeException {

    private static final ApiResultCode ERROR_CODE = ApiResultCode.INVALID_GANG_SHEET_LAYOUT;
    private final String code;
    private final String errorMessage;
    private final HttpStatus httpStatus;
    private final List<ApiErrorDetail> details;

    public GangSheetLayoutException(List<ApiErrorDetail> details) {
        super(ERROR_CODE.getMessage());

        this.code = ERROR_CODE.getCode();
        this.errorMessage = ERROR_CODE.getMessage();
        this.httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
        this.details = details == null ? List.of() : List.copyOf(details);
    }
}
