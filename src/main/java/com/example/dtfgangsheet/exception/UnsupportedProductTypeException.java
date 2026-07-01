package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.model.ProductType;
import org.springframework.http.HttpStatus;

public class UnsupportedProductTypeException extends AppException {

    public UnsupportedProductTypeException(ProductType productType) {
        super(
                ApiResultCode.UNSUPPORTED_PRODUCT_TYPE,
                HttpStatus.BAD_REQUEST,
                "Product type is not supported yet: " + productType
        );
    }
}
