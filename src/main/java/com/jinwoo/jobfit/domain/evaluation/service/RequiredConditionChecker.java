package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ConditionCheckResult;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirement;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RequiredConditionChecker {

    private final JobRequirementParser jobRequirementParser;
    private final Clock clock;

    public ConditionCheckResult check(UserProfile userProfile, JobPosting jobPosting) {
        JobRequirement requirement = jobRequirementParser.parse(jobPosting);

        List<String> failedReasons = new ArrayList<>();
        checkCareer(userProfile, requirement, failedReasons);
        checkEmploymentType(userProfile, requirement, failedReasons);
        checkDeadline(jobPosting, failedReasons);

        return failedReasons.isEmpty() ? ConditionCheckResult.pass() : ConditionCheckResult.fail(failedReasons);
    }

    private void checkCareer(UserProfile userProfile, JobRequirement requirement, List<String> failedReasons) {
        if (userProfile.getCareerLevel() != CareerLevel.NEW_GRADUATE || requirement.newGraduateAllowed()) {
            return;
        }

        if (requirement.minYears() != null) {
            failedReasons.add("경력 %d년 이상 요구, 신입 지원 불가".formatted(requirement.minYears()));
        } else {
            failedReasons.add("경력 지원 불가 공고, 신입 지원 불가");
        }
    }

    private void checkEmploymentType(UserProfile userProfile, JobRequirement requirement, List<String> failedReasons) {
        EmploymentType desired = userProfile.getEmploymentType();
        if (desired == EmploymentType.ANY) {
            return;
        }
        if (desired != requirement.employmentType()) {
            failedReasons.add("희망 고용형태(%s)와 공고 고용형태(%s) 불일치".formatted(desired, requirement.employmentType()));
        }
    }

    private void checkDeadline(JobPosting jobPosting, List<String> failedReasons) {
        LocalDateTime expirationAt = jobPosting.getExpirationAt();
        if (expirationAt != null && expirationAt.isBefore(LocalDateTime.now(clock))) {
            failedReasons.add("마감일이 지난 공고입니다.");
        }
    }
}
