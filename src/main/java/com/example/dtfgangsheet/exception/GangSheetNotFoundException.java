package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class GangSheetNotFoundException extends AppException {

    public GangSheetNotFoundException(String id) {
        super(ApiResultCode.GANG_SHEET_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Gang sheet not found: " + id);
    }
}
