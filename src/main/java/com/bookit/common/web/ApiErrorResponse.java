package com.bookit.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        int status,
        Instant timestamp,
        List<ValidationError> invalidFields
) {
    public static ApiErrorResponse of(String code, String message, int status) {
        return new ApiErrorResponse(code, message, status, Instant.now(), null);
    }

    public static ApiErrorResponse withValidationErrors(String code, String message, int status, List<ValidationError> invalidFields) {
        return new ApiErrorResponse(code, message, status, Instant.now(), invalidFields);
    }

    public record ValidationError(String field, String message) {}
}