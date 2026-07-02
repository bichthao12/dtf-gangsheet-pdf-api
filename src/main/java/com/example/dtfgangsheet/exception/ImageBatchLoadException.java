package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiErrorDetail;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Getter
public class ImageBatchLoadException extends ImageLoadException {

    private static final Map<ApiResultCode, HttpStatus> IMAGE_HTTP_STATUS = Map.of(
            ApiResultCode.IMAGE_LOAD_ERROR,         HttpStatus.BAD_REQUEST,
            ApiResultCode.IMAGE_SIZE_EXCEEDED,      HttpStatus.BAD_REQUEST,
            ApiResultCode.IMAGE_NOT_FOUND,          HttpStatus.NOT_FOUND,
            ApiResultCode.UNSUPPORTED_IMAGE_FORMAT, HttpStatus.BAD_REQUEST,
            ApiResultCode.IMAGE_FETCH_ERROR,        HttpStatus.BAD_GATEWAY
    );

    private final List<ApiErrorDetail> details;

    public ImageBatchLoadException(List<ApiErrorDetail> details) {
        super(
                resolveResultCode(details),
                resolveHttpStatus(details),
                "Image batch load failed: " + details.size() + " error(s)"
        );
        this.details = List.copyOf(details);
    }

    private static ApiResultCode resolveResultCode(List<ApiErrorDetail> details) {
        return uniformErrorCode(details)
                .flatMap(ApiResultCode::fromCode)
                .orElse(ApiResultCode.IMAGE_LOAD_ERROR);
    }

    private static HttpStatus resolveHttpStatus(List<ApiErrorDetail> details) {
        return uniformErrorCode(details)
                .flatMap(ApiResultCode::fromCode)
                .map(code -> IMAGE_HTTP_STATUS.getOrDefault(code, HttpStatus.BAD_REQUEST))
                .orElse(HttpStatus.BAD_REQUEST);
    }

    /** Trả về code nếu mọi lỗi cùng một code; mixed batch → empty. */
    private static Optional<String> uniformErrorCode(List<ApiErrorDetail> details) {
        if (details == null || details.isEmpty()) {
            return Optional.empty();
        }
        String first = details.getFirst().code();
        boolean same = details.stream()
                .map(ApiErrorDetail::code)
                .allMatch(c -> Objects.equals(c, first));
        return same ? Optional.of(first) : Optional.empty();
    }
}
