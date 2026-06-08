package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ImageBatchLoadException.class)
    public ResponseEntity<ApiResponse<Void>> handleImageBatch(ImageBatchLoadException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(
                        ex.getResultCode().getCode(),
                        ex.getResultCode().getMessage(),
                        ex.getErrors()
                ));
    }

    @ExceptionHandler(ImageLoadException.class)
    public ResponseEntity<ApiResponse<Void>> handleImageLoad(ImageLoadException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getResultCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ServerException.class)
    public ResponseEntity<ApiResponse<Void>> handleServer(ServerException ex) {
        log.error("Server error", ex);
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getResultCode().getCode(), ex.getResultCode().getMessage()));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getResultCode().getCode(), ex.getMessage()));
    }
}
