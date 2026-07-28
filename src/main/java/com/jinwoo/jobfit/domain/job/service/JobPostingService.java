package com.jinwoo.jobfit.domain.job.service;

import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.dto.JobPostingResponse;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;

    @Transactional
    public JobPostingResponse create(JobPostingCreateRequest request) {
        if (jobPostingRepository.existsBySourceAndExternalId(request.source(), request.externalId())) {
            throw new IllegalStateException("이미 등록된 공고입니다. source=" + request.source() + ", externalId=" + request.externalId());
        }

        JobPosting saved = jobPostingRepository.save(request.toEntity());
        return JobPostingResponse.from(saved);
    }
}