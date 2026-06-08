package com.example.dtfgangsheet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(

        String code,
        String message,
        String requestId,
        T data,

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<ApiErrorDetail> errors

) {

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(code, message, UUID.randomUUID().toString(), data, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, UUID.randomUUID().toString(), null, null);
    }

    public static ApiResponse<Void> error(String code, String message, List<ApiErrorDetail> errors) {
        return new ApiResponse<>(code, message, UUID.randomUUID().toString(), null, errors);
    }
}