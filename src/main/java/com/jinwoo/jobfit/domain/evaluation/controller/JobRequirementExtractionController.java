package com.jinwoo.jobfit.domain.evaluation.controller;

import com.jinwoo.jobfit.domain.evaluation.dto.ReExtractionResponse;
import com.jinwoo.jobfit.domain.evaluation.service.JobRequirementExtractionService;
import com.jinwoo.jobfit.domain.user.service.ProfileEditPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobRequirementExtractionController {

    private final JobRequirementExtractionService jobRequirementExtractionService;
    private final ProfileEditPolicy profileEditPolicy;

    @PostMapping("/{id}/re-extract")
    public ResponseEntity<ReExtractionResponse> reExtract(@PathVariable Long id) {
        profileEditPolicy.assertJobDataEditable();
        return ResponseEntity.ok(jobRequirementExtractionService.reExtract(id));
    }
}
