package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class TransientImageLoadException extends ImageLoadException {

    public TransientImageLoadException(String detail) {
        super(ApiResultCode.IMAGE_FETCH_ERROR, HttpStatus.BAD_GATEWAY, detail);
    }

    public TransientImageLoadException(String detail, Throwable cause) {
        super(ApiResultCode.IMAGE_FETCH_ERROR, HttpStatus.BAD_GATEWAY, detail, cause);
    }
}