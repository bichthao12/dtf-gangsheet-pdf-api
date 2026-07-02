package com.example.dtfgangsheet.dto.common;

import com.example.dtfgangsheet.config.TraceIdFilter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/** Success envelope — controllers return this on HTTP 2xx. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiResponse<T>(

        int status,
        String code,
        String message,
        String traceId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        Instant timestamp,
        T data

) {

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return success(code, message, data, HttpStatus.OK);
    }

    public static <T> ApiResponse<T> success(String code, String message, T data, HttpStatus httpStatus) {
        return new ApiResponse<>(
                httpStatus.value(),
                code,
                message,
                TraceIdFilter.currentTraceId(),
                Instant.now(),
                data
        );
    }
}
