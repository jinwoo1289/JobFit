package com.jinwoo.jobfit.domain.user.repository;

import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvaluationWeightRepository extends JpaRepository<EvaluationWeight, Long> {

    Optional<EvaluationWeight> findByUserProfileId(Long userProfileId);
}
