package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class GangSheetConfirmedException extends AppException {

    public GangSheetConfirmedException(String id) {
        super(ApiResultCode.GANG_SHEET_CONFIRMED, HttpStatus.CONFLICT,
                "Gang sheet is confirmed and cannot be modified: " + id);
    }
}
