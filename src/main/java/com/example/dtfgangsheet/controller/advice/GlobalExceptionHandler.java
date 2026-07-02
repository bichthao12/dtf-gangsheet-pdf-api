package com.example.dtfgangsheet.controller.advice;

import com.example.dtfgangsheet.config.TraceIdFilter;
import com.example.dtfgangsheet.dto.common.ApiErrorDetail;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.dto.common.ErrorResponse;
import com.example.dtfgangsheet.exception.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Maps exceptions to {@link ErrorResponse} + HTTP status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException ex) {
        String message = ApiResultCode.INVALID_JSON.getMessage();

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife) {
            String field = ife.getPath().isEmpty() ? "unknown"
                    : ife.getPath().getLast().getFieldName();
            message = "Invalid value for field '%s': expected %s but got '%s'"
                    .formatted(field, ife.getTargetType().getSimpleName(), ife.getValue());
        }

        return ErrorResponse.of(
                ApiResultCode.INVALID_JSON.getCode(),
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ApiErrorDetail> details = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> ApiErrorDetail.of(
                        ApiResultCode.VALIDATION_ERROR.getCode(),
                        fe.getField(),
                        fe.getDefaultMessage()))
                .toList();

        return ErrorResponse.of(
                ApiResultCode.VALIDATION_ERROR,
                ApiResultCode.VALIDATION_ERROR.getMessage(),
                HttpStatus.BAD_REQUEST,
                details
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '%s'".formatted(ex.getName());
        return ErrorResponse.of(
                ApiResultCode.BAD_REQUEST.getCode(),
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorDetail> details = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    int dot = path.indexOf('.');
                    String field = dot >= 0 ? path.substring(dot + 1) : path;
                    return ApiErrorDetail.of(
                            ApiResultCode.VALIDATION_ERROR.getCode(),
                            field,
                            cv.getMessage());
                })
                .toList();

        return ErrorResponse.of(
                ApiResultCode.VALIDATION_ERROR,
                ApiResultCode.VALIDATION_ERROR.getMessage(),
                HttpStatus.BAD_REQUEST,
                details
        );
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(GangSheetLayoutException.class)
    public ErrorResponse handleGangSheetLayout(GangSheetLayoutException ex) {
        return ErrorResponse.of(
                ex.getResultCode().getCode(),
                ex.getResultCode().getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getDetails()
        );
    }

    @ExceptionHandler(ImageBatchLoadException.class)
    public ErrorResponse handleImageBatch(ImageBatchLoadException ex, HttpServletResponse response) {
        HttpStatus status = ex.getHttpStatus();
        response.setStatus(status.value());
        return ErrorResponse.of(
                ex.getResultCode().getCode(),
                ex.getResultCode().getMessage(),
                status,
                ex.getDetails()
        );
    }

    @ExceptionHandler(ImageLoadException.class)
    public ErrorResponse handleImageLoad(ImageLoadException ex, HttpServletResponse response) {
        HttpStatus status = ex.getHttpStatus();
        response.setStatus(status.value());
        return ErrorResponse.of(ex.getResultCode(), ex.getMessage(), status);
    }

    @ExceptionHandler(ServerException.class)
    public ErrorResponse handleServer(ServerException ex, HttpServletResponse response) {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        log.error("Server error traceId={}", MDC.get(TraceIdFilter.MDC_KEY), ex);
        return ErrorResponse.of(ex.getResultCode(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AppException.class)
    public ErrorResponse handleApp(AppException ex, HttpServletResponse response) {
        HttpStatus status = ex.getHttpStatus();
        response.setStatus(status.value());
        return ErrorResponse.of(ex.getResultCode(), ex.getMessage(), status);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ErrorResponse.of(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
