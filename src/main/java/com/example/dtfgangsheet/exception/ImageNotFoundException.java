package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class ImageNotFoundException extends ImageLoadException {

    public ImageNotFoundException(String detail) {
        super(ApiResultCode.IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND, detail);
    }

    public ImageNotFoundException(String detail, Throwable cause) {
        super(ApiResultCode.IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND, detail, cause);
    }
}