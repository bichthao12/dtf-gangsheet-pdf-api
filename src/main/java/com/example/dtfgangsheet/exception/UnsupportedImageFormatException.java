package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class UnsupportedImageFormatException extends ImageLoadException {

    public UnsupportedImageFormatException(String detail) {
        super(ApiResultCode.UNSUPPORTED_IMAGE_FORMAT, HttpStatus.BAD_REQUEST, detail);
    }

    public UnsupportedImageFormatException(String detail, Throwable cause) {
        super(ApiResultCode.UNSUPPORTED_IMAGE_FORMAT, HttpStatus.BAD_REQUEST, detail, cause);
    }
}