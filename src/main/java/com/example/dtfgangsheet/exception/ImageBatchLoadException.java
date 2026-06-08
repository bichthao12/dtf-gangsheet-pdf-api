package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiErrorDetail;
import lombok.Getter;

import java.util.List;

@Getter
public class ImageBatchLoadException extends ImageLoadException {

    private final List<ApiErrorDetail> errors;

    public ImageBatchLoadException(List<ApiErrorDetail> errors) {
        super("Image batch load failed: " + errors.size() + " error(s)");
        this.errors = errors;
    }
}