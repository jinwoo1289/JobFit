package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirement;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobRequirementParser {

    private static final Pattern MIN_YEARS_PATTERN = Pattern.compile("경력\\s*(\\d+)년\\s*이상");
    private static final Pattern MAX_YEARS_PATTERN = Pattern.compile("(\\d+)년\\s*이하");
    private static final String INTERN_KEYWORD = "인턴";

    public JobRequirement parse(JobPosting jobPosting) {
        String experienceLevel = jobPosting.getExperienceLevel() == null ? "" : jobPosting.getExperienceLevel();

        Integer minYears = extractYears(MIN_YEARS_PATTERN, experienceLevel);
        Integer maxYears = extractYears(MAX_YEARS_PATTERN, experienceLevel);
        boolean newGraduateAllowed = isNewGraduateAllowed(experienceLevel, minYears);
        EmploymentType employmentType = resolveEmploymentType(jobPosting.getTitle(), experienceLevel);

        return new JobRequirement(minYears, maxYears, newGraduateAllowed, employmentType);
    }

    private boolean isNewGraduateAllowed(String experienceLevel, Integer minYears) {
        if (minYears != null && minYears > 0) {
            return false;
        }
        return experienceLevel.contains("신입")
                || experienceLevel.contains("무관");
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
