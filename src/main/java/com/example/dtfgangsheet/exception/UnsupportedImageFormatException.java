package com.example.dtfgangsheet.exception;

public class UnsupportedImageFormatException extends ImageLoadException {

    public UnsupportedImageFormatException(String detail) {
        super(detail);
    }

    public UnsupportedImageFormatException(String detail, Throwable cause) {
        super(detail, cause);
    }
}