package com.jinwoo.jobfit.domain.job.exception;

public class DuplicateJobPostingException extends RuntimeException {
    public DuplicateJobPostingException(String message) {
        super(message);
    }
}
