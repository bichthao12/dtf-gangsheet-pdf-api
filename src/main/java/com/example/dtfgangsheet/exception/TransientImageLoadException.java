package com.example.dtfgangsheet.exception;

public class TransientImageLoadException extends ImageLoadException {

    public TransientImageLoadException(String detail) {
        super(detail);
    }

    public TransientImageLoadException(String detail, Throwable cause) {
        super(detail, cause);
    }
}