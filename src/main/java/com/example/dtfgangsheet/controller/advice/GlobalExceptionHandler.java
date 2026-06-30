package com.example.dtfgangsheet.controller.advice;

import com.example.dtfgangsheet.dto.common.ApiErrorDetail;
import com.example.dtfgangsheet.dto.common.ApiResponse;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.exception.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // Spring / Jackson errors
    // -------------------------------------------------------------------------

    /** JSON malformed hoặc sai kiểu — quantity: 3.5 vào int */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = ApiResultCode.INVALID_JSON.getMessage();

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife) {
            String field = ife.getPath().isEmpty() ? "unknown"
                    : ife.getPath().getLast().getFieldName();
            message = "Invalid value for field '%s': expected %s but got '%s'"
                    .formatted(field, ife.getTargetType().getSimpleName(), ife.getValue());
        }

        return ApiResponse.error(ApiResultCode.INVALID_JSON.getCode(), message);
    }

    /** @Valid trên object — ví dụ @RequestBody SomeObject */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ApiErrorDetail> errors = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> ApiErrorDetail.of(
                        ApiResultCode.VALIDATION_ERROR.getCode(),
                        fe.getField(),
                        fe.getDefaultMessage()
                ))
                .toList();

        return ApiResponse.error(
                ApiResultCode.VALIDATION_ERROR.getCode(),
                ApiResultCode.VALIDATION_ERROR.getMessage(),
                errors
        );
    }

    /** Query/path param sai kiểu — ví dụ status=INVALID */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName();
        String message = "Invalid value for parameter '%s'".formatted(field);
        return ApiResponse.error(ApiResultCode.BAD_REQUEST.getCode(), message);
    }

    /** @Valid trên List<@Valid T> hoặc @Validated trên controller method params */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    // Bỏ prefix method name: "nest.requests[0].quantity" → "requests[0].quantity"
                    int dot = path.indexOf('.');
                    String field = dot >= 0 ? path.substring(dot + 1) : path;
                    return ApiErrorDetail.of(
                            ApiResultCode.VALIDATION_ERROR.getCode(),
                            field,
                            cv.getMessage()
                    );
                })
                .toList();

        return ApiResponse.error(
                ApiResultCode.VALIDATION_ERROR.getCode(),
                ApiResultCode.VALIDATION_ERROR.getMessage(),
                errors
        );
    }

    // -------------------------------------------------------------------------
    // App exceptions — từ cụ thể đến tổng quát
    // -------------------------------------------------------------------------

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(GangSheetLayoutException.class)
    public ApiResponse<Void> handleGangSheetLayout(GangSheetLayoutException ex) {
        return ApiResponse.error(
                ex.getResultCode().getCode(),
                ex.getResultCode().getMessage(),
                ex.getDetails()
        );
    }

    @ExceptionHandler(ImageBatchLoadException.class)
    public ApiResponse<Void> handleImageBatch(ImageBatchLoadException ex, HttpServletResponse response) {
        response.setStatus(ex.getHttpStatus().value());
        return ApiResponse.error(
                ex.getResultCode().getCode(),
                ex.getResultCode().getMessage(),
                ex.getErrors()
        );
    }

    /**
     * ImageLoadException và subclass chưa được handle ở trên.
     * Dùng HttpServletResponse để set status từ exception thay vì hardcode @ResponseStatus —
     * tránh override httpStatus của subclass (TransientImageLoadException=502, ImageNotFoundException=404).
     */
    @ExceptionHandler(ImageLoadException.class)
    public ApiResponse<Void> handleImageLoad(ImageLoadException ex, HttpServletResponse response) {
        response.setStatus(ex.getHttpStatus().value());
        return ApiResponse.error(ex.getResultCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler(ServerException.class)
    public ApiResponse<Void> handleServer(ServerException ex) {
        String requestId = UUID.randomUUID().toString();
        log.error("Server error requestId={}", requestId, ex);
        return new ApiResponse<>(
                ex.getResultCode().getCode(),
                ex.getResultCode().getMessage(),
                requestId,   // trả requestId về client để support có thể lookup log
                null, null
        );
    }

    /** AppException còn lại — httpStatus lấy từ exception */
    @ExceptionHandler(AppException.class)
    public ApiResponse<Void> handleApp(AppException ex, HttpServletResponse response) {
        response.setStatus(ex.getHttpStatus().value());
        return ApiResponse.error(ex.getResultCode().getCode(), ex.getMessage());
    }

    /** Fallback — bắt tất cả exception không mong muốn */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiResponse.error(
                ApiResultCode.INTERNAL_ERROR.getCode(),
                ApiResultCode.INTERNAL_ERROR.getMessage()
        );
    }
}