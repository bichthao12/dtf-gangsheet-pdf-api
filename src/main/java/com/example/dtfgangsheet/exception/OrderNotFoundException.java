package com.example.dtfgangsheet.exception;

import com.example.dtfgangsheet.dto.common.ApiResultCode;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends AppException {

    public OrderNotFoundException(String id) {
        super(ApiResultCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Order not found: " + id);
    }
}
