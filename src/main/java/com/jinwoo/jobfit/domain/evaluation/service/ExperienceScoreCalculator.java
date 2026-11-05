package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.UserProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ExperienceScoreCalculator {

    private static final double NO_MATCH_SCORE = 0;

    private final SkillAliasNormalizer skillAliasNormalizer;

    public double calculate(List<UserProject> projects, List<String> requiredSkills) {
        return calculateForGroups(projects, singletonGroups(requiredSkills));
    }

    // 택1 그룹은 그룹 안의 기술 중 하나라도 프로젝트에서 사용했으면 필수 기술 1개를 사용한 것으로 센다.
    public double calculateForGroups(List<UserProject> projects, List<List<String>> requiredSkillGroups) {
        if (requiredSkillGroups.isEmpty() || projects.isEmpty()) {
            return NO_MATCH_SCORE;
        }

        Set<Set<String>> normalizedGroups = normalize(requiredSkillGroups);

        return projects.stream()
                .mapToDouble(project -> matchRatio(project, normalizedGroups))
                .max()
                .orElse(NO_MATCH_SCORE);
    }

    // 경험 점수의 분모와 같은 기준으로, 정규화 후 중복을 제거한 필수 기술(택1 그룹은 1개) 개수.
    public int requiredSkillCount(List<List<String>> requiredSkillGroups) {
        return normalize(requiredSkillGroups).size();
    }

    // 경험 점수를 결정한 프로젝트(필수 기술이 가장 많이 겹치는 프로젝트)에서 사용한 필수 기술 개수.
    public int bestMatchedCount(List<UserProject> projects, List<List<String>> requiredSkillGroups) {
        Set<Set<String>> normalizedGroups = normalize(requiredSkillGroups);
        return (int) projects.stream()
                .mapToLong(project -> matchedCount(project, normalizedGroups))
                .max()
                .orElse(0);
    }

    private static List<List<String>> singletonGroups(List<String> skills) {
        return skills.stream().map(List::of).toList();
    }

    private Set<Set<String>> normalize(List<List<String>> requiredSkillGroups) {
        return requiredSkillGroups.stream()
                .map(group -> group.stream()
                        .map(skillAliasNormalizer::normalize)
                        .collect(Collectors.toSet()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private double matchRatio(UserProject project, Set<Set<String>> normalizedGroups) {
        return (matchedCount(project, normalizedGroups) * 100.0) / normalizedGroups.size();
    }

    private long matchedCount(UserProject project, Set<Set<String>> normalizedGroups) {
        Set<String> techStack = project.getTechStack().stream()
                .map(skillAliasNormalizer::normalize)
                .collect(Collectors.toSet());

        return normalizedGroups.stream()
                .filter(group -> group.stream().anyMatch(skill -> skillAliasNormalizer.matches(skill, techStack)))
                .count();
    }
}
