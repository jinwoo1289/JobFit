package com.jinwoo.jobfit.domain.job.repository;

import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    boolean existsBySourceAndExternalId(
            JobSource source,
            String externalId
    );
}
