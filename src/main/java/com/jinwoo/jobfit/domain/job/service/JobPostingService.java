package com.jinwoo.jobfit.domain.job.service;

import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.dto.JobPostingResponse;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.exception.DuplicateJobPostingException;
import com.jinwoo.jobfit.domain.job.exception.JobPostingNotFoundException;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;

    @Transactional
    public JobPostingResponse create(JobPostingCreateRequest request) {
        if (jobPostingRepository.existsBySourceAndExternalId(request.source(), request.externalId())) {
            throw new DuplicateJobPostingException("이미 등록된 공고입니다. source=" + request.source() + ", externalId=" + request.externalId());
        }

        JobPosting saved = jobPostingRepository.save(request.toEntity());
        return JobPostingResponse.from(saved);
    }

    public List<JobPostingResponse> findAll() {
        return jobPostingRepository.findAll().stream()
                .map(JobPostingResponse::from)
                .toList();
    }

    public JobPostingResponse findById(Long id) {
        JobPosting jobPosting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new JobPostingNotFoundException("공고를 찾을 수 없습니다. id=" + id));
        return JobPostingResponse.from(jobPosting);
    }
}