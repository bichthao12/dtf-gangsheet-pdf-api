package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiErrorDetail;
import com.example.dtfgangsheet.dto.ApiResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** ConstraintViolationException — xảy ra khi validate List<@Valid T> */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        List<ApiErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    // Path dạng: nest.requests[0].quantity → lấy phần cuối
                    String path = cv.getPropertyPath().toString();
                    return ApiErrorDetail.of(
                            "VALIDATION_ERROR",
                            path,
                            cv.getMessage()   // message từ @Positive, @NotBlank, v.v.
                    );
                })
                .toList();

        return ApiResponse.error("VALIDATION_FAILED", "Request validation failed", errors);
    }
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = "Invalid request body";

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife) {
            String fieldName = ife.getPath().isEmpty() ? "unknown"
                    : ife.getPath().getLast().getFieldName();
            message = "Invalid value for field '%s': %s".formatted(fieldName, ife.getValue());
        }

        return ApiResponse.error("INVALID_REQUEST_BODY", message);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorDetail> errors = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> new ApiErrorDetail(
                        "VALIDATION_ERROR",
                        fe.getField(),
                        fe.getDefaultMessage()
                ))
                .toList();

        return ApiResponse.error("VALIDATION_FAILED", "Request validation failed", errors);
    }

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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleImageBatch(ImageBatchLoadException ex) {
        return ApiResponse.error(
                ex.getResultCode().getCode(),
                ex.getResultCode().getMessage(),
                ex.getErrors()
        );
    }

    @ExceptionHandler(ImageLoadException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleImageLoad(ImageLoadException ex) {
        return ApiResponse.error(ex.getResultCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler(ServerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleServer(ServerException ex) {
        log.error("Server error", ex);
        return ApiResponse.error(ex.getResultCode().getCode(), ex.getResultCode().getMessage());
    }

    @ExceptionHandler(AppException.class)
    public ApiResponse<Void> handleApp(AppException ex) {
        return ApiResponse.error(ex.getResultCode().getCode(), ex.getMessage());
    }
}