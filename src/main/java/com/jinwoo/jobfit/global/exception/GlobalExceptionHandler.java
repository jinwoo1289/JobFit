package com.jinwoo.jobfit.global.exception;

import com.jinwoo.jobfit.domain.evaluation.exception.LlmResponseException;
import com.jinwoo.jobfit.domain.job.exception.DuplicateJobPostingException;
import com.jinwoo.jobfit.domain.job.exception.JobPostingNotFoundException;
import com.jinwoo.jobfit.domain.user.exception.InvalidEvaluationWeightException;
import com.jinwoo.jobfit.domain.user.exception.ProfileEditDisabledException;
import com.jinwoo.jobfit.domain.user.exception.UserProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.", errors));
    }

    // 잘못된 enum 값(예: employmentType=PART)이나 깨진 JSON 본문
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다. 값의 형식을 확인해 주세요."));
    }

    @ExceptionHandler(ProfileEditDisabledException.class)
    public ResponseEntity<ErrorResponse> handleProfileEditDisabled(ProfileEditDisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage()));
    }

    @ExceptionHandler(JobPostingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobPostingNotFound(JobPostingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(DuplicateJobPostingException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateJobPosting(DuplicateJobPostingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage()));
    }

    @ExceptionHandler(InvalidEvaluationWeightException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEvaluationWeight(InvalidEvaluationWeightException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserProfileNotFound(UserProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(LlmResponseException.class)
    public ResponseEntity<ErrorResponse> handleLlmResponse(LlmResponseException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(HttpStatus.BAD_GATEWAY, e.getMessage()));
    }
}
