package com.jinwoo.jobfit.global.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public record ErrorResponse(
        int status,
        String message,
        List<FieldError> errors
) {
    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(status.value(), message, List.of());
    }

    public static ErrorResponse of(HttpStatus status, String message, List<FieldError> errors) {
        return new ErrorResponse(status.value(), message, errors);
    }

    public record FieldError(String field, String reason) {
    }
}
