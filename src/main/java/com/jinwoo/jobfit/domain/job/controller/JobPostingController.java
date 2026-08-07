package com.jinwoo.jobfit.domain.job.controller;

import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.dto.JobPostingResponse;
import com.jinwoo.jobfit.domain.job.service.JobPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<JobPostingResponse>> findAll() {
        return ResponseEntity.ok(jobPostingService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(jobPostingService.findById(id));
    }
}