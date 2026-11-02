package com.jinwoo.jobfit.domain.user.service;

import com.jinwoo.jobfit.domain.job.exception.JobPostingNotFoundException;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import com.jinwoo.jobfit.domain.user.dto.JobApplicationResponse;
import com.jinwoo.jobfit.domain.user.exception.UserProfileNotFoundException;
import com.jinwoo.jobfit.domain.user.repository.JobApplicationRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final Clock clock;

    // 멱등: 이미 체크된 공고면 기존 체크 시각을 유지한 채 그대로 반환한다.
    @Transactional
    public JobApplicationResponse markApplied(Long userProfileId, Long jobPostingId) {
        validateExists(userProfileId, jobPostingId);
        jobApplicationRepository.insertIfAbsent(userProfileId, jobPostingId, LocalDateTime.now(clock));
        return jobApplicationRepository.findByUserProfileIdAndJobPostingId(userProfileId, jobPostingId)
                .map(JobApplicationResponse::from)
                .orElseThrow(() -> new IllegalStateException(
                        "지원 완료 기록 저장 후 조회 실패: userProfileId=%d, jobPostingId=%d".formatted(userProfileId, jobPostingId)));
    }

    // 멱등: 체크되지 않은 공고를 해제해도 오류 없이 끝난다.
    @Transactional
    public void unmarkApplied(Long userProfileId, Long jobPostingId) {
        validateExists(userProfileId, jobPostingId);
        jobApplicationRepository.deleteByUserProfileIdAndJobPostingId(userProfileId, jobPostingId);
    }

    private void validateExists(Long userProfileId, Long jobPostingId) {
        if (!userProfileRepository.existsById(userProfileId)) {
            throw new UserProfileNotFoundException("프로필을 찾을 수 없습니다. id=" + userProfileId);
        }
        if (!jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("공고를 찾을 수 없습니다. id=" + jobPostingId);
        }
    }
}
