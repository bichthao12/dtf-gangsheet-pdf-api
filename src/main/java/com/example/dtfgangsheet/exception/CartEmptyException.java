package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class CartEmptyException extends AppException {

    public CartEmptyException() {
        super(ApiResultCode.CART_EMPTY, HttpStatus.BAD_REQUEST, "Cart is empty");
    }
}
