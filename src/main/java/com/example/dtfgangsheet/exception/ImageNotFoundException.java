package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiResultCode;
import org.springframework.http.HttpStatus;

public class ImageNotFoundException extends ImageLoadException {

    public ImageNotFoundException(String detail) {
        super(detail);
    }

    public ImageNotFoundException(String detail, Throwable cause) {
        super(detail, cause);
    }
}