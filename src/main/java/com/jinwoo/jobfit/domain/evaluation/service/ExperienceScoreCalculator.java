package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ExperienceScoreCalculator {

    private static final double NO_MENTION_SCORE = 0;

    public double calculate(List<UserProject> projects, JobPosting jobPosting) {
        Set<String> techStacks = projects.stream()
                .flatMap(project -> project.getTechStack().stream())
                .map(this::normalize)
                .collect(Collectors.toSet());

        if (techStacks.isEmpty()) {
            return NO_MENTION_SCORE;
        }

        String description = normalize(jobPosting.getDescription());

        long mentionedCount = techStacks.stream()
                .filter(techStack -> !techStack.isEmpty() && description.contains(techStack))
                .count();

        return (mentionedCount * 100.0) / techStacks.size();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
