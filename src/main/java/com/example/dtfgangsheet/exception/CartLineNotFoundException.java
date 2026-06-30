package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class CartLineNotFoundException extends AppException {

    public CartLineNotFoundException(String lineId) {
        super(ApiResultCode.CART_LINE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Cart line not found: " + lineId);
    }
}
