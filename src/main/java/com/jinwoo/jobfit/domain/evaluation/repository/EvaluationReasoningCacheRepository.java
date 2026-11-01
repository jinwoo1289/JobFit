package com.jinwoo.jobfit.domain.evaluation.repository;

import com.jinwoo.jobfit.domain.evaluation.entity.EvaluationReasoningCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvaluationReasoningCacheRepository extends JpaRepository<EvaluationReasoningCacheEntity, Long> {

    Optional<EvaluationReasoningCacheEntity> findByJobPostingIdAndUserProfileId(Long jobPostingId, Long userProfileId);
}
