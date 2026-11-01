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
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RequiredConditionChecker {

    static final String IT_GENERAL_NOTICE = "IT 통합 채용 공고입니다. 입사 후 개발·운영 중 배치될 수 있으니 직무 범위를 확인하세요.";

    // 희망 직무는 자유 입력 문자열이므로, 소문자로 정규화한 뒤 아래 키워드 포함 여부로 개발 계열인지 판단한다.
    private static final List<String> DEVELOPMENT_JOB_KEYWORDS = List.of(
            "개발", "엔지니어", "프로그래머", "백엔드", "프론트엔드", "풀스택", "서버", "소프트웨어",
            "developer", "engineer", "programmer", "backend", "frontend", "fullstack", "sw"
    );

    private final JobRequirementParser jobRequirementParser;
    private final Clock clock;

    public ConditionCheckResult check(UserProfile userProfile, JobPosting jobPosting, JobRequirementExtraction extraction) {
        JobRequirement requirement = jobRequirementParser.parse(jobPosting);

        List<String> failedReasons = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        checkCareer(userProfile, requirement, extraction, failedReasons);
        checkEmploymentType(userProfile, requirement, failedReasons);
        checkDeadline(jobPosting, failedReasons);
        checkMaxYears(userProfile, extraction, failedReasons);
        checkJobCategory(userProfile, extraction, failedReasons, notices);

        return failedReasons.isEmpty()
                ? ConditionCheckResult.pass(notices)
                : ConditionCheckResult.fail(failedReasons, notices);
    }

    private void checkCareer(UserProfile userProfile, JobRequirement requirement,
                             JobRequirementExtraction extraction, List<String> failedReasons) {
        if (userProfile.getCareerLevel() != CareerLevel.NEW_GRADUATE || requirement.newGraduateAllowed()) {
            return;
        }

        // 판정은 experienceLevel 기준 그대로 두고, 문구의 연수만 experienceLevel에 없으면 본문 추출 결과로 보완한다.
        // (예: experienceLevel "경력" + 본문 "5년 이상 ... 경험자")
        Integer minYears = requirement.minYears() != null ? requirement.minYears() : extraction.minYears();
        if (minYears != null) {
            failedReasons.add("경력 %d년 이상 요구, 신입 지원 불가".formatted(minYears));
        } else {
            failedReasons.add("경력직 채용 공고, 신입 지원 불가");
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

    private void checkJobCategory(UserProfile userProfile, JobRequirementExtraction extraction,
                                  List<String> failedReasons, List<String> notices) {
        // jobCategory가 null이면 판정 불가(예: 정규식 fallback)로 보고 통과시킨다. OTHER만 명시적으로 무관한 직무로 취급한다.
        if (extraction.jobCategory() == JobCategory.OTHER) {
            failedReasons.add("공고 직무 카테고리(OTHER)가 희망 직무와 무관합니다.");
            return;
        }
        // IT 통합 채용은 개발·운영 어느 쪽으로 배치될지 모르므로, 개발 계열 희망자만 통과시키고 직무 범위 확인을 안내한다.
        if (extraction.jobCategory() == JobCategory.IT_GENERAL) {
            if (isDevelopmentJob(userProfile.getDesiredJob())) {
                notices.add(IT_GENERAL_NOTICE);
            } else {
                failedReasons.add("IT 통합 채용 공고로, 희망 직무(%s)가 개발 계열이 아닙니다.".formatted(userProfile.getDesiredJob()));
            }
        }
    }

    private boolean isDevelopmentJob(String desiredJob) {
        if (desiredJob == null) {
            return false;
        }
        String normalized = desiredJob.toLowerCase(Locale.ROOT);
        return DEVELOPMENT_JOB_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}
