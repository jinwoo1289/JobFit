package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

public record SkillMatchResult(List<String> matchedSkills, List<String> unmatchedSkills) {
}
