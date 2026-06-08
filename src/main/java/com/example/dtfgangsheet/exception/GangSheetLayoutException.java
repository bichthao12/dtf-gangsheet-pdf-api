package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiErrorDetail;
import com.example.dtfgangsheet.dto.ApiResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class GangSheetLayoutException extends AppException {

    private final List<ApiErrorDetail> details;

    public GangSheetLayoutException(List<ApiErrorDetail> details) {
        super(ApiResultCode.INVALID_GANG_SHEET_LAYOUT, HttpStatus.UNPROCESSABLE_ENTITY);
        this.details = details == null ? List.of() : List.copyOf(details);
    }
}