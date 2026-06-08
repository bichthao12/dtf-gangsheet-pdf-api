package com.example.dtfgangsheet.model;

/**
 * Input nội bộ cho thuật toán nesting.
 *
 * NestingRequest chỉ dùng ở tầng HTTP/API.
 * NestingInput dùng bên trong service/nesting engine.
 */
public record NestingInput(
        String img,
        double width,
        double height,
        int quantity
) {
}