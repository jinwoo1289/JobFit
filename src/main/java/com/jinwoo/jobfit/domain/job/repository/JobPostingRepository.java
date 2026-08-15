package com.jinwoo.jobfit.domain.job.repository;

import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    boolean existsBySourceAndExternalId(
            JobSource source,
            String externalId
    );

    Optional<JobPosting> findBySourceAndExternalId(
            JobSource source,
            String externalId
    );
}
