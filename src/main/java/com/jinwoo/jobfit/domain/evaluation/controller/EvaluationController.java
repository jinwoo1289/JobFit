package com.jinwoo.jobfit.domain.evaluation.controller;

import com.jinwoo.jobfit.domain.evaluation.dto.EvaluationRequest;
import com.jinwoo.jobfit.domain.evaluation.dto.EvaluationResponse;
import com.jinwoo.jobfit.domain.evaluation.service.EvaluationQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationQueryService evaluationQueryService;

    @PostMapping
    public ResponseEntity<List<EvaluationResponse>> evaluateAll(@Valid @RequestBody EvaluationRequest request) {
        return ResponseEntity.ok(evaluationQueryService.evaluateAll(request.userProfileId()));
    }

    @GetMapping("/{userProfileId}/jobs/{jobPostingId}")
    public ResponseEntity<EvaluationResponse> evaluateOne(@PathVariable Long userProfileId,
                                                            @PathVariable Long jobPostingId) {
        return ResponseEntity.ok(evaluationQueryService.evaluateOne(userProfileId, jobPostingId));
    }
}
