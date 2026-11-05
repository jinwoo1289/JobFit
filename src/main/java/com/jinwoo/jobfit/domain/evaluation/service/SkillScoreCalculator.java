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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SkillScoreCalculator {

    private static final double FULL_MATCH_SCORE = 100;
    private static final double SKILL_ONLY_SCORE = 60;
    private static final double NO_MATCH_SCORE = 0;
    // 택1 그룹이 전부 미충족일 때 "Linux 또는 Windows"처럼 묶어 표시한다.
    static final String ANY_OF_DELIMITER = " 또는 ";
    // 우대 점수 분모의 상한. 우대 기술이 이보다 많아도 이 개수만큼 충족하면 만점이다.
    static final int PREFERRED_SCORE_CAP = 5;

    private final SkillAliasNormalizer skillAliasNormalizer;

    public SkillScore calculate(List<UserSkill> skills, List<UserProject> projects, List<String> targetSkills) {
        return calculateForGroups(skills, projects, singletonGroups(targetSkills));
    }

    // 우대 기술은 회사 기술 스택을 길게 나열한 경우가 많아, 전체 개수로 나누면 몇 개를 보유해도 점수가 낮게 나온다.
    // (토스페이먼츠 54905826: 우대 9개 중 3개 보유 → 33.3) 분모를 최대 PREFERRED_SCORE_CAP개로 제한하고 100점을 넘지 않게 한다.
    public SkillScore calculatePreference(List<UserSkill> skills, List<UserProject> projects, List<String> preferredSkills) {
        if (preferredSkills.isEmpty()) {
            return SkillScore.notEvaluated();
        }

        Set<String> userSkillNames = userSkillNames(skills);
        Set<String> userTechStacks = userTechStacks(projects);

        double sum = preferredSkills.stream()
                .mapToDouble(skill -> scoreOf(skillAliasNormalizer.normalize(skill), userSkillNames, userTechStacks))
                .sum();
        int denominator = Math.min(preferredSkills.size(), PREFERRED_SCORE_CAP);
        return SkillScore.of(Math.min(FULL_MATCH_SCORE, sum / denominator));
    }

    // 그룹 하나를 기술 1개로 보고, 그룹 점수는 그룹 안에서 가장 잘 맞는 기술의 점수로 한다.
    // 단일 기술은 원소 1개짜리 그룹이므로 기존 계산과 같다.
    public SkillScore calculateForGroups(List<UserSkill> skills, List<UserProject> projects, List<List<String>> targetGroups) {
        if (targetGroups.isEmpty()) {
            return SkillScore.notEvaluated();
        }

        Set<String> userSkillNames = userSkillNames(skills);
        Set<String> userTechStacks = userTechStacks(projects);

        double average = targetGroups.stream()
                .mapToDouble(group -> group.stream()
                        .mapToDouble(skill -> scoreOf(skillAliasNormalizer.normalize(skill), userSkillNames, userTechStacks))
                        .max()
                        .orElse(NO_MATCH_SCORE))
                .average()
                .orElse(NO_MATCH_SCORE);
        return SkillScore.of(average);
    }

    // EvaluationReasoningGenerator가 LLM에게 일치/불일치 여부를 직접 판단하게 하지 않고,
    // 이미 계산된 결과를 그대로 문장 작성에만 사용하도록 하기 위한 상세 매칭 결과.
    public SkillMatchResult matchDetail(List<UserSkill> skills, List<UserProject> projects, List<String> targetSkills) {
        return matchDetailForGroups(skills, projects, singletonGroups(targetSkills));
    }

    // 그룹 중 하나라도 보유하면 일치로 보고, 일치 목록에는 실제로 보유한 기술(여럿이면 점수가 가장 높은 기술)을 넣는다.
    // 그룹 전체를 보유하지 않았을 때만 불일치 목록에 "A 또는 B" 형태로 넣는다.
    public SkillMatchResult matchDetailForGroups(List<UserSkill> skills, List<UserProject> projects, List<List<String>> targetGroups) {
        Set<String> userSkillNames = userSkillNames(skills);
        Set<String> userTechStacks = userTechStacks(projects);

        List<String> matchedSkills = new ArrayList<>();
        List<String> unmatchedSkills = new ArrayList<>();
        for (List<String> group : targetGroups) {
            bestMatchedMember(group, userSkillNames, userTechStacks).ifPresentOrElse(
                    matchedSkills::add,
                    () -> unmatchedSkills.add(String.join(ANY_OF_DELIMITER, group)));
        }
        return new SkillMatchResult(matchedSkills, unmatchedSkills);
    }

    // 일치한 필수 기술마다 그 기술을 사용한 프로젝트명을 붙인다. 판단 근거 문장(matchedPoints)과 같은 기준으로 판정한다.
    public List<MatchedSkill> matchedWithProjects(List<String> matchedSkills, List<UserProject> projects) {
        return matchedSkills.stream()
                .map(skill -> new MatchedSkill(skill, projectsUsing(skillAliasNormalizer.normalize(skill), projects)))
                .toList();
    }

    private Optional<String> bestMatchedMember(List<String> group, Set<String> userSkillNames, Set<String> userTechStacks) {
        String best = null;
        double bestScore = NO_MATCH_SCORE;
        for (String skill : group) {
            double score = scoreOf(skillAliasNormalizer.normalize(skill), userSkillNames, userTechStacks);
            if (score > bestScore) {
                best = skill;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private List<String> projectsUsing(String normalizedSkill, List<UserProject> projects) {
        return projects.stream()
                .filter(project -> skillAliasNormalizer.matches(normalizedSkill, project.getTechStack().stream()
                        .map(skillAliasNormalizer::normalize)
                        .collect(Collectors.toSet())))
                .map(UserProject::getProjectName)
                .toList();
    }

    private static List<List<String>> singletonGroups(List<String> skills) {
        return skills.stream().map(List::of).toList();
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
