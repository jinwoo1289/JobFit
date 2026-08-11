package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private final SkillScoreCalculator skillScoreCalculator;
    private final ExperienceScoreCalculator experienceScoreCalculator;

    public ScoreResult calculate(List<UserSkill> skills, List<UserProject> projects, JobPosting jobPosting) {
        double skillScore = skillScoreCalculator.calculate(skills, projects, jobPosting);
        double experienceScore = experienceScoreCalculator.calculate(projects, jobPosting);
        return new ScoreResult(skillScore, experienceScore);
    }
}
