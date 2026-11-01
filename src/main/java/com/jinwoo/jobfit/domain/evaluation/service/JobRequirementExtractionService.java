package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.dto.ReExtractionResponse;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.exception.JobPostingNotFoundException;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobRequirementExtractionService {

    private final JobRequirementExtractor jobRequirementExtractor;
    private final JobPostingRepository jobPostingRepository;

    public ReExtractionResponse reExtract(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("공고를 찾을 수 없습니다. id=" + jobPostingId));

        return ReExtractionResponse.from(jobPostingId, jobRequirementExtractor.reExtract(jobPosting));
    }
}
