package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.UserProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ExperienceScoreCalculator {

    private static final double NO_MATCH_SCORE = 0;

    private final SkillAliasNormalizer skillAliasNormalizer;

    public double calculate(List<UserProject> projects, List<String> requiredSkills) {
        if (requiredSkills.isEmpty() || projects.isEmpty()) {
            return NO_MATCH_SCORE;
        }

        Set<String> normalizedRequiredSkills = normalize(requiredSkills);

        return projects.stream()
                .mapToDouble(project -> matchRatio(project, normalizedRequiredSkills))
                .max()
                .orElse(NO_MATCH_SCORE);
    }

    // 경험 점수의 분모와 같은 기준으로, 정규화 후 중복을 제거한 필수 기술 개수.
    public int requiredSkillCount(List<String> requiredSkills) {
        return normalize(requiredSkills).size();
    }

    // 경험 점수를 결정한 프로젝트(필수 기술이 가장 많이 겹치는 프로젝트)에서 사용한 필수 기술 개수.
    public int bestMatchedCount(List<UserProject> projects, List<String> requiredSkills) {
        Set<String> normalizedRequiredSkills = normalize(requiredSkills);
        return (int) projects.stream()
                .mapToLong(project -> matchedCount(project, normalizedRequiredSkills))
                .max()
                .orElse(0);
    }

    private Set<String> normalize(List<String> requiredSkills) {
        return requiredSkills.stream()
                .map(skillAliasNormalizer::normalize)
                .collect(Collectors.toSet());
    }

    private double matchRatio(UserProject project, Set<String> normalizedRequiredSkills) {
        return (matchedCount(project, normalizedRequiredSkills) * 100.0) / normalizedRequiredSkills.size();
    }

    private long matchedCount(UserProject project, Set<String> normalizedRequiredSkills) {
        Set<String> techStack = project.getTechStack().stream()
                .map(skillAliasNormalizer::normalize)
                .collect(Collectors.toSet());

        return normalizedRequiredSkills.stream()
                .filter(skill -> skillAliasNormalizer.matches(skill, techStack))
                .count();
    }
}
