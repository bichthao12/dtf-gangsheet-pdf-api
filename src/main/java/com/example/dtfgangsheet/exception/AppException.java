package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final ApiResultCode resultCode;
    private final HttpStatus httpStatus;

    protected AppException(ApiResultCode resultCode, HttpStatus httpStatus) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.httpStatus = httpStatus;
    }

    protected AppException(ApiResultCode resultCode, HttpStatus httpStatus, String detail) {
        super(detail);
        this.resultCode = resultCode;
        this.httpStatus = httpStatus;
    }

    protected AppException(ApiResultCode resultCode, HttpStatus httpStatus, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.resultCode = resultCode;
        this.httpStatus = httpStatus;
    }
}