package com.example.dtfgangsheet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of(
                "status", 400,
                "message", safeMessage(ex, "Invalid request")
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        return Map.of(
                "status", 400,
                "message", "Validation failed",
                "errors", errors
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ImageLoadException.class)
    public Map<String, Object> handleImageLoad(ImageLoadException ex) {
        return Map.of(
                "status", 400,
                "message", safeMessage(ex, "Cannot load image")
        );
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IOException.class)
    public Map<String, Object> handleIo(IOException ex) {
        return Map.of(
                "status", 500,
                "message", safeMessage(ex, "I/O error while generating PDF")
        );
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleGeneric(Exception ex) {
        return Map.of(
                "status", 500,
                "message", safeMessage(ex, "Unexpected error")
        );
    }

    private String safeMessage(Throwable ex, String fallback) {
        return Objects.requireNonNullElse(ex.getMessage(), fallback);
    }
}
