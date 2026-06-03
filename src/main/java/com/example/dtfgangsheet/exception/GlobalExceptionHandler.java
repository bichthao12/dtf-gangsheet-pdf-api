package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiErrorDetail;
import com.example.dtfgangsheet.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "Invalid request",
                List.of(new ApiErrorDetail(null, safeMessage(ex, "Invalid request")))
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorDetail> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorDetail(
                        normalizeField(error.getField()),
                        error.getDefaultMessage()
                ))
                .toList();

        return buildError(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed",
                errors
        );
    }

    @ExceptionHandler(ImageLoadException.class)
    public ResponseEntity<ApiResponse<Void>> handleImageLoad(ImageLoadException ex) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                "IMAGE_LOAD_ERROR",
                "Cannot load image",
                List.of(new ApiErrorDetail(null, safeMessage(ex, "Cannot load image")))
        );
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchFileException ex) {
        return buildError(
                HttpStatus.NOT_FOUND,
                "PDF_NOT_FOUND",
                "PDF not found",
                null
        );
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Void>> handleIo(IOException ex) {
        log.error("I/O error while generating PDF", ex);
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PDF_IO_ERROR",
                "I/O error while generating PDF",
                null
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "Malformed request body",
                List.of(new ApiErrorDetail(null, "Request body must be valid JSON"))
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Unexpected error",
                null
        );
    }

    private ResponseEntity<ApiResponse<Void>> buildError(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorDetail> errors
    ) {
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(code, message, errors));
    }

    private String safeMessage(Throwable ex, String fallback) {
        return Objects.requireNonNullElse(ex.getMessage(), fallback);
    }

    private String normalizeField(String field) {
        return field
                .replaceFirst("^generatePdf\\.", "")
                .replaceFirst("^arg0\\.", "")
                .replaceFirst("^arg0", "")
                .replaceFirst("^items\\.", "")
                .replaceFirst("^items", "");
    }
}
