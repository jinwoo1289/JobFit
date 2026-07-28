package com.jinwoo.jobfit.domain.job.controller;

import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.dto.JobPostingResponse;
import com.jinwoo.jobfit.domain.job.service.JobPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @PostMapping
    public ResponseEntity<JobPostingResponse> create(@Valid @RequestBody JobPostingCreateRequest request) {
        JobPostingResponse response = jobPostingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}