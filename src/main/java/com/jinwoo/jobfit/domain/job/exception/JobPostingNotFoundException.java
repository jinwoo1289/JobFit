package com.jinwoo.jobfit.domain.job.exception;

public class JobPostingNotFoundException extends RuntimeException {
    public JobPostingNotFoundException(String message) {
        super(message);
    }
}
