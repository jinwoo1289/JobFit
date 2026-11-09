package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ExperienceLevelType;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirement;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobRequirementParser {

    private static final Pattern MIN_YEARS_PATTERN = Pattern.compile("경력\\s*(\\d+)\\s*년\\s*(?:이상|↑)");
    private static final Pattern MAX_YEARS_PATTERN = Pattern.compile("(\\d+)년\\s*이하");
    private static final String INTERN_KEYWORD = "인턴";
    private static final String EXPERIENCED_KEYWORD = "경력";
    private static final List<String> NEW_GRADUATE_KEYWORDS = List.of("신입", INTERN_KEYWORD, "무관");

    public JobRequirement parse(JobPosting jobPosting) {
        String experienceLevel = jobPosting.getExperienceLevel() == null ? "" : jobPosting.getExperienceLevel();

        Integer minYears = extractYears(MIN_YEARS_PATTERN, experienceLevel);
        Integer maxYears = extractYears(MAX_YEARS_PATTERN, experienceLevel);
        ExperienceLevelType experienceLevelType = classify(experienceLevel);
        EmploymentType employmentType = resolveEmploymentType(jobPosting.getTitle(), experienceLevel);

        return new JobRequirement(minYears, maxYears, experienceLevelType, employmentType);
    }

    // "신입/경력 3년 이상"처럼 신입과 경력 하한이 함께 표기되면 하한은 경력자에게만 적용되므로,
    // "신입"·"인턴"·"무관"이 명시된 경우 minYears와 관계없이 신입 지원 가능으로 본다.
    // 이들 없이 "경력"만 있으면 경력 전용, 둘 다 없으면(빈 값·인식 못한 값) 판정 불가로 둔다.
    private ExperienceLevelType classify(String experienceLevel) {
        if (NEW_GRADUATE_KEYWORDS.stream().anyMatch(experienceLevel::contains)) {
            return ExperienceLevelType.NEW_GRADUATE_ALLOWED;
        }
        if (experienceLevel.contains(EXPERIENCED_KEYWORD)) {
            return ExperienceLevelType.EXPERIENCED_ONLY;
        }
        return ExperienceLevelType.UNKNOWN;
    }

    private EmploymentType resolveEmploymentType(String title, String experienceLevel) {
        boolean isIntern = (title != null && title.contains(INTERN_KEYWORD))
                || experienceLevel.contains(INTERN_KEYWORD);
        return isIntern ? EmploymentType.INTERN : EmploymentType.FULL_TIME;
    }

    private Integer extractYears(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
