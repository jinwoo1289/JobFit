package com.jinwoo.jobfit.domain.evaluation.repository;

import com.jinwoo.jobfit.domain.evaluation.entity.JobRequirementExtractionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobRequirementExtractionRepository extends JpaRepository<JobRequirementExtractionEntity, Long> {

    Optional<JobRequirementExtractionEntity> findByJobPostingId(Long jobPostingId);
}
