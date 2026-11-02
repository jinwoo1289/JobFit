package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.MatchedSkill;
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

        List<String> matchedSkills = new ArrayList<>();
        List<String> unmatchedSkills = new ArrayList<>();
        for (String targetSkill : targetSkills) {
            String normalized = skillAliasNormalizer.normalize(targetSkill);
            if (skillAliasNormalizer.matches(normalized, userSkillNames)) {
                matchedSkills.add(targetSkill);
            } else {
                unmatchedSkills.add(targetSkill);
            }
        }
        return new SkillMatchResult(matchedSkills, unmatchedSkills);
    }

    // 일치한 필수 기술마다 그 기술을 사용한 프로젝트명을 붙인다. 판단 근거 문장(matchedPoints)과 같은 기준으로 판정한다.
    public List<MatchedSkill> matchedWithProjects(List<String> matchedSkills, List<UserProject> projects) {
        return matchedSkills.stream()
                .map(skill -> new MatchedSkill(skill, projectsUsing(skillAliasNormalizer.normalize(skill), projects)))
                .toList();
    }

    private List<String> projectsUsing(String normalizedSkill, List<UserProject> projects) {
        return projects.stream()
                .filter(project -> skillAliasNormalizer.matches(normalizedSkill, project.getTechStack().stream()
                        .map(skillAliasNormalizer::normalize)
                        .collect(Collectors.toSet())))
                .map(UserProject::getProjectName)
                .toList();
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
        if (!skillAliasNormalizer.matches(normalizedTarget, userSkillNames)) {
            return NO_MATCH_SCORE;
        }
        return skillAliasNormalizer.matches(normalizedTarget, userTechStacks) ? FULL_MATCH_SCORE : SKILL_ONLY_SCORE;
    }
}
