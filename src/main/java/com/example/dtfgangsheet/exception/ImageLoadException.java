package com.example.dtfgangsheet.exception;

import java.io.IOException;

public class ImageLoadException extends IOException {

    public ImageLoadException(String message) {
        super(message);
    }

    public ImageLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
