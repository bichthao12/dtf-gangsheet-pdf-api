package com.example.dtfgangsheet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<ApiErrorDetail> errors
) {

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(
                true,
                code,
                message,
                data,
                null
        );
    }

    public static <T> ApiResponse<T> error(
            String code,
            String message,
            List<ApiErrorDetail> errors
    ) {
        return new ApiResponse<>(
                false,
                code,
                message,
                null,
                errors
        );
    }
}
