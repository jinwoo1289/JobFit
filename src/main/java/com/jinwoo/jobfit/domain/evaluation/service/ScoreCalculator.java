package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
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
    private final CertificateScoreCalculator certificateScoreCalculator;

    public ScoreResult calculate(List<UserSkill> skills,
                                  List<UserProject> projects,
                                  List<UserCertificate> certificates,
                                  JobPosting jobPosting,
                                  JobRequirementExtraction extraction) {
        double skillScore = skillScoreCalculator.calculate(skills, projects, extraction.requiredSkills());
        double preferenceScore = skillScoreCalculator.calculate(skills, projects, extraction.preferredSkills());
        double experienceScore = experienceScoreCalculator.calculate(projects, jobPosting);
        double certificateScore = certificateScoreCalculator.calculate(certificates, extraction.requiredCertificates());
        return new ScoreResult(skillScore, experienceScore, preferenceScore, certificateScore);
    }
}
