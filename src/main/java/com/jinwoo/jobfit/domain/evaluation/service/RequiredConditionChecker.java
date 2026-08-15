package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ConditionCheckResult;
import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirement;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
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

    public ConditionCheckResult check(UserProfile userProfile, JobPosting jobPosting, JobRequirementExtraction extraction) {
        JobRequirement requirement = jobRequirementParser.parse(jobPosting);

        List<String> failedReasons = new ArrayList<>();
        checkCareer(userProfile, requirement, failedReasons);
        checkEmploymentType(userProfile, requirement, failedReasons);
        checkDeadline(jobPosting, failedReasons);
        checkMaxYears(userProfile, extraction, failedReasons);
        checkJobCategory(extraction, failedReasons);

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

    private void checkMaxYears(UserProfile userProfile, JobRequirementExtraction extraction, List<String> failedReasons) {
        Integer maxYears = extraction.maxYears();
        Integer yearsOfExperience = userProfile.getYearsOfExperience();
        if (maxYears != null && yearsOfExperience != null && yearsOfExperience > maxYears) {
            failedReasons.add("경력 %d년 이하 요구, 지원자 경력 %d년 초과".formatted(maxYears, yearsOfExperience));
        }
    }

    private void checkJobCategory(JobRequirementExtraction extraction, List<String> failedReasons) {
        // jobCategory가 null이면 판정 불가(예: 정규식 fallback)로 보고 통과시킨다. OTHER만 명시적으로 무관한 직무로 취급한다.
        if (extraction.jobCategory() == JobCategory.OTHER) {
            failedReasons.add("공고 직무 카테고리(OTHER)가 희망 직무와 무관합니다.");
        }
    }
}
