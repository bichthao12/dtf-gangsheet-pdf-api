package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiResultCode;
import org.springframework.http.HttpStatus;

public class ImageLoadException extends AppException {

    public ImageLoadException(String detail) {
        super(ApiResultCode.IMAGE_LOAD_ERROR, HttpStatus.BAD_REQUEST, detail);
    }

    public ImageLoadException(String detail, Throwable cause) {
        super(ApiResultCode.IMAGE_LOAD_ERROR, HttpStatus.BAD_REQUEST, cause);
    }

    // constructor cho subclass override ApiResultCode
    protected ImageLoadException(ApiResultCode resultCode, HttpStatus httpStatus, String detail) {
        super(resultCode, httpStatus, detail);
    }
}