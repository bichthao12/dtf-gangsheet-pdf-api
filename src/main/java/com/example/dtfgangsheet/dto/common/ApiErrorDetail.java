package com.example.dtfgangsheet.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDetail(
        String code,
        String field,
        String message,
        Integer index,
        String source
) {

    /** Lỗi validation đơn giản — không có index, không có source */
    public static ApiErrorDetail of(String code, String field, String message) {
        return new ApiErrorDetail(code, field, message, null, null);
    }

    /** Lỗi theo item trong list — có index */
    public static ApiErrorDetail itemError(String code, int index, String field, String message) {
        return new ApiErrorDetail(code, field, message, index, null);
    }

    /** Lỗi load ảnh — có index và source url */
    public static ApiErrorDetail imageError(int index, String source, String code, String message) {
        return new ApiErrorDetail(code, "items[" + index + "].img", message, index, source);
    }
}