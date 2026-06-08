package com.example.dtfgangsheet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDetail(
        String code,
        String field,
        String message,
        Integer index,
        String source
) {

    public ApiErrorDetail(String code, String field, String message) {
        this(code, field, message, null, null);
    }

    public ApiErrorDetail(String source, String code, String field, String message) {
        this(code, field, message, null, source);
    }

    public static ApiErrorDetail of(
            String code,
            String field,
            String message
    ) {
        return new ApiErrorDetail(code, field, message);
    }

    public static ApiErrorDetail itemError(
            String code,
            int index,
            String field,
            String message
    ) {
        return new ApiErrorDetail(
                code,
                field,
                message,
                index,
                null
        );
    }

    public static ApiErrorDetail imageError(
            int index,
            String source,
            String code,
            String message
    ) {
        return new ApiErrorDetail(
                code,
                "items[" + index + "].img",
                message,
                index,
                source
        );
    }
}
