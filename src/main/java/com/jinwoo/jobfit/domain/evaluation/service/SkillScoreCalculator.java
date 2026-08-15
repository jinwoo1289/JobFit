package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SkillScoreCalculator {

    private static final double FULL_MATCH_SCORE = 100;
    private static final double SKILL_ONLY_SCORE = 60;
    private static final double NO_MATCH_SCORE = 0;

    public double calculate(List<UserSkill> skills, List<UserProject> projects, List<String> targetSkills) {
        if (targetSkills.isEmpty()) {
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

        return targetSkills.stream()
                .mapToDouble(targetSkill -> scoreOf(normalize(targetSkill), userSkillNames, userTechStacks))
                .average()
                .orElse(NO_MATCH_SCORE);
    }

    private double scoreOf(String targetSkill, Set<String> userSkillNames, Set<String> userTechStacks) {
        if (!userSkillNames.contains(targetSkill)) {
            return NO_MATCH_SCORE;
        }
        return userTechStacks.contains(targetSkill) ? FULL_MATCH_SCORE : SKILL_ONLY_SCORE;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
