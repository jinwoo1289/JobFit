package com.jinwoo.jobfit.domain.user.controller;

import com.jinwoo.jobfit.domain.user.dto.JobApplicationResponse;
import com.jinwoo.jobfit.domain.user.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/profile")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @PutMapping("/{profileId}/jobs/{jobId}/applied")
    public ResponseEntity<JobApplicationResponse> markApplied(@PathVariable Long profileId, @PathVariable Long jobId) {
        return ResponseEntity.ok(jobApplicationService.markApplied(profileId, jobId));
    }

    @DeleteMapping("/{profileId}/jobs/{jobId}/applied")
    public ResponseEntity<Void> unmarkApplied(@PathVariable Long profileId, @PathVariable Long jobId) {
        jobApplicationService.unmarkApplied(profileId, jobId);
        return ResponseEntity.noContent().build();
    }
}
