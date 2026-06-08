package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.ApiErrorDetail;
import com.example.dtfgangsheet.dto.ApiResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ImageBatchLoadException extends ImageLoadException {

    private final List<ApiErrorDetail> errors;

    public ImageBatchLoadException(List<ApiErrorDetail> errors) {
        super("Image batch load failed: " + errors.size() + " error(s)");
        this.errors = errors;
    }
}