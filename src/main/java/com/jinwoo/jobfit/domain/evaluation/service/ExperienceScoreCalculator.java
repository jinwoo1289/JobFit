package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.UserProject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ExperienceScoreCalculator {

    private static final double NO_MATCH_SCORE = 0;

    public double calculate(List<UserProject> projects, List<String> requiredSkills) {
        if (requiredSkills.isEmpty() || projects.isEmpty()) {
            return NO_MATCH_SCORE;
        }

        Set<String> normalizedRequiredSkills = requiredSkills.stream()
                .map(this::normalize)
                .collect(Collectors.toSet());

        return projects.stream()
                .mapToDouble(project -> matchRatio(project, normalizedRequiredSkills))
                .max()
                .orElse(NO_MATCH_SCORE);
    }

    private double matchRatio(UserProject project, Set<String> normalizedRequiredSkills) {
        Set<String> techStack = project.getTechStack().stream()
                .map(this::normalize)
                .collect(Collectors.toSet());

        long matchedCount = normalizedRequiredSkills.stream()
                .filter(techStack::contains)
                .count();

        return (matchedCount * 100.0) / normalizedRequiredSkills.size();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
