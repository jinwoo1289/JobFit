package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

// 화면에서 프로젝트명을 태그로 보여줄 수 있도록, 일치한 필수 기술과 그 기술을 사용한 프로젝트를 구조화해 담는다.
public record MatchedSkill(String skill, List<String> projectNames) {
}
