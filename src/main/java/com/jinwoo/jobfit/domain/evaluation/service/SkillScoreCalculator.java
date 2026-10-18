package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.SkillMatchResult;
import com.jinwoo.jobfit.domain.evaluation.vo.SkillScore;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SkillScoreCalculator {

    private static final double FULL_MATCH_SCORE = 100;
    private static final double SKILL_ONLY_SCORE = 60;
    private static final double NO_MATCH_SCORE = 0;

    private final SkillAliasNormalizer skillAliasNormalizer;

    public SkillScore calculate(List<UserSkill> skills, List<UserProject> projects, List<String> targetSkills) {
        if (targetSkills.isEmpty()) {
            return SkillScore.notEvaluated();
        }

        Set<String> userSkillNames = userSkillNames(skills);
        Set<String> userTechStacks = userTechStacks(projects);

        double average = targetSkills.stream()
                .mapToDouble(targetSkill -> scoreOf(skillAliasNormalizer.normalize(targetSkill), userSkillNames, userTechStacks))
                .average()
                .orElse(NO_MATCH_SCORE);
        return SkillScore.of(average);
    }

    // EvaluationReasoningGenerator가 LLM에게 일치/불일치 여부를 직접 판단하게 하지 않고,
    // 이미 계산된 결과를 그대로 문장 작성에만 사용하도록 하기 위한 상세 매칭 결과.
    public SkillMatchResult matchDetail(List<UserSkill> skills, List<UserProject> projects, List<String> targetSkills) {
        Set<String> userSkillNames = userSkillNames(skills);
        Set<String> userTechStacks = userTechStacks(projects);

        List<String> matchedSkills = new ArrayList<>();
        List<String> unmatchedSkills = new ArrayList<>();
        for (String targetSkill : targetSkills) {
            String normalized = skillAliasNormalizer.normalize(targetSkill);
            if (matches(normalized, userSkillNames, userTechStacks)) {
                matchedSkills.add(targetSkill);
            } else {
                unmatchedSkills.add(targetSkill);
            }
        }
        return new SkillMatchResult(matchedSkills, unmatchedSkills);
    }

    private Set<String> userSkillNames(List<UserSkill> skills) {
        return skills.stream()
                .map(UserSkill::getSkillName)
                .map(skillAliasNormalizer::normalize)
                .collect(Collectors.toSet());
    }

    private Set<String> userTechStacks(List<UserProject> projects) {
        return projects.stream()
                .flatMap(project -> project.getTechStack().stream())
                .map(skillAliasNormalizer::normalize)
                .collect(Collectors.toSet());
    }

    private double scoreOf(String normalizedTarget, Set<String> userSkillNames, Set<String> userTechStacks) {
        if (!matches(normalizedTarget, userSkillNames, userTechStacks)) {
            return NO_MATCH_SCORE;
        }
        return matchesTechStack(normalizedTarget, userTechStacks) ? FULL_MATCH_SCORE : SKILL_ONLY_SCORE;
    }

    private boolean matches(String normalizedTarget, Set<String> userSkillNames, Set<String> userTechStacks) {
        if (skillAliasNormalizer.isCategoryAlias(normalizedTarget)) {
            return skillAliasNormalizer.matchesCategoryEquivalent(normalizedTarget, userSkillNames);
        }
        return userSkillNames.contains(normalizedTarget);
    }

    private boolean matchesTechStack(String normalizedTarget, Set<String> userTechStacks) {
        if (skillAliasNormalizer.isCategoryAlias(normalizedTarget)) {
            return skillAliasNormalizer.matchesCategoryEquivalent(normalizedTarget, userTechStacks);
        }
        return userTechStacks.contains(normalizedTarget);
    }
}
