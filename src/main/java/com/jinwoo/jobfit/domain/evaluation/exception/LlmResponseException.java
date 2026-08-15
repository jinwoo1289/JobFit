package com.jinwoo.jobfit.domain.evaluation.exception;

public class LlmResponseException extends RuntimeException {
    public LlmResponseException(String message) {
        super(message);
    }
}
