package com.example.dtfgangsheet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDetail(
        String field,
        String message
) {
}
