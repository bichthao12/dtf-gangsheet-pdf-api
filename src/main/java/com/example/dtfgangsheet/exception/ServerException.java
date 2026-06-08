package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class ServerException extends AppException {

    public ServerException(ApiResultCode resultCode) {
        super(resultCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ServerException(ApiResultCode resultCode, Throwable cause) {
        super(resultCode, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}