package com.jinwoo.jobfit.domain.user.controller;

import com.jinwoo.jobfit.domain.user.dto.EvaluationWeightResponse;
import com.jinwoo.jobfit.domain.user.dto.EvaluationWeightUpdateRequest;
import com.jinwoo.jobfit.domain.user.dto.UserProfileCreateRequest;
import com.jinwoo.jobfit.domain.user.dto.ProfileSettingsResponse;
import com.jinwoo.jobfit.domain.user.dto.UserProfileResponse;
import com.jinwoo.jobfit.domain.user.dto.UserProfileUpdateRequest;
import com.jinwoo.jobfit.domain.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PostMapping
    public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody UserProfileCreateRequest request) {
        UserProfileResponse response = userProfileService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 프론트가 프로필 화면을 읽기 전용으로 보여줄지 판단하는 용도. "/{id}"보다 리터럴 경로가 우선 매칭된다.
    @GetMapping("/settings")
    public ResponseEntity<ProfileSettingsResponse> settings() {
        return ResponseEntity.ok(userProfileService.settings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.findById(id));
    }

    @PutMapping("/{id}/weights")
    public ResponseEntity<EvaluationWeightResponse> updateWeights(@PathVariable Long id,
                                                                    @Valid @RequestBody EvaluationWeightUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateWeights(id, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfileResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
