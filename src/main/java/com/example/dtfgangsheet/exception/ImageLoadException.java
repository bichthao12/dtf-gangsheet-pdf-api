package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class ImageLoadException extends AppException {

    public ImageLoadException(String detail) {
        super(ApiResultCode.IMAGE_LOAD_ERROR, HttpStatus.BAD_REQUEST, detail);
    }

    public ImageLoadException(String detail, Throwable cause) {
        super(ApiResultCode.IMAGE_LOAD_ERROR, HttpStatus.BAD_REQUEST, detail, cause);
    }

    protected ImageLoadException(ApiResultCode resultCode, HttpStatus httpStatus, String detail) {
        super(resultCode, httpStatus, detail);
    }

    protected ImageLoadException(ApiResultCode resultCode, HttpStatus httpStatus,
                                 String detail, Throwable cause) {
        super(resultCode, httpStatus, detail, cause);  // gọi đúng AppException constructor 4 params
    }
}