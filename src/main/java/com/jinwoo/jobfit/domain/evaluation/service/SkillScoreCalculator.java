package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SkillScoreCalculator {

    private static final double FULL_MATCH_SCORE = 100;
    private static final double SKILL_ONLY_SCORE = 60;
    private static final double NO_MATCH_SCORE = 0;

    public double calculate(List<UserSkill> skills, List<UserProject> projects, JobPosting jobPosting) {
        List<String> requiredSkills = parseKeywords(jobPosting.getKeywords());
        if (requiredSkills.isEmpty()) {
            return NO_MATCH_SCORE;
        }

        Set<String> userSkillNames = skills.stream()
                .map(UserSkill::getSkillName)
                .map(this::normalize)
                .collect(Collectors.toSet());

        Set<String> userTechStacks = projects.stream()
                .flatMap(project -> project.getTechStack().stream())
                .map(this::normalize)
                .collect(Collectors.toSet());

        return requiredSkills.stream()
                .mapToDouble(requiredSkill -> scoreOf(normalize(requiredSkill), userSkillNames, userTechStacks))
                .average()
                .orElse(NO_MATCH_SCORE);
    }

    private double scoreOf(String requiredSkill, Set<String> userSkillNames, Set<String> userTechStacks) {
        if (!userSkillNames.contains(requiredSkill)) {
            return NO_MATCH_SCORE;
        }
        return userTechStacks.contains(requiredSkill) ? FULL_MATCH_SCORE : SKILL_ONLY_SCORE;
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
