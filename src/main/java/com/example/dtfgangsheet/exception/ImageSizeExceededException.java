package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiResultCode;
import org.springframework.http.HttpStatus;

public class ImageSizeExceededException extends ImageLoadException {

    public ImageSizeExceededException(String detail) {
        super(ApiResultCode.IMAGE_SIZE_EXCEEDED, HttpStatus.BAD_REQUEST, detail);
    }
}