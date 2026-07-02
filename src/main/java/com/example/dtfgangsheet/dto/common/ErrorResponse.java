package com.example.dtfgangsheet.dto.common;

import com.example.dtfgangsheet.config.TraceIdFilter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/** Failure envelope — returned by {@code GlobalExceptionHandler} on HTTP 4xx/5xx. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ErrorResponse(

        boolean success,
        int status,
        String error,
        String code,
        String message,
        String traceId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        Instant timestamp,

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<ApiErrorDetail> details

) {

    public static ErrorResponse of(String code, String message, HttpStatus httpStatus) {
        return of(code, message, httpStatus, null);
    }

    public static ErrorResponse of(
            String code,
            String message,
            HttpStatus httpStatus,
            List<ApiErrorDetail> details
    ) {
        return new ErrorResponse(
                false,
                httpStatus.value(),
                httpStatus.name(),
                code,
                message,
                TraceIdFilter.currentTraceId(),
                Instant.now(),
                details
        );
    }

    public static ErrorResponse of(ApiResultCode resultCode, HttpStatus httpStatus) {
        return of(resultCode.getCode(), resultCode.getMessage(), httpStatus);
    }

    public static ErrorResponse of(ApiResultCode resultCode, String message, HttpStatus httpStatus) {
        return of(resultCode.getCode(), message, httpStatus);
    }

    public static ErrorResponse of(
            ApiResultCode resultCode,
            String message,
            HttpStatus httpStatus,
            List<ApiErrorDetail> details
    ) {
        return of(resultCode.getCode(), message, httpStatus, details);
    }
}
