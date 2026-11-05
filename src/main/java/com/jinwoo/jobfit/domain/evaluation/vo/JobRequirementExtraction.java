package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;
import java.util.stream.Stream;

// requiredAnyOf: "Linux 또는 Windows"처럼 그중 하나만 보유하면 되는 필수 기술 그룹 목록.
// 그룹 하나는 필수 기술 1개로 계산하며, 그룹 안의 기술은 requiredSkills에 중복으로 넣지 않는다.
public record JobRequirementExtraction(
        List<String> requiredSkills,
        List<List<String>> requiredAnyOf,
        List<String> preferredSkills,
        Integer minYears,
        Integer maxYears,
        boolean newGraduateAllowed,
        List<String> requiredCertificates,
        List<String> preferredCertificates,
        JobCategory jobCategory
) {

    public JobRequirementExtraction {
        requiredAnyOf = requiredAnyOf == null ? List.of() : requiredAnyOf;
    }

    // 택1 그룹이 없는 추출 결과(정규식 fallback, 테스트 등)를 만들 때 쓴다.
    public JobRequirementExtraction(List<String> requiredSkills,
                                    List<String> preferredSkills,
                                    Integer minYears,
                                    Integer maxYears,
                                    boolean newGraduateAllowed,
                                    List<String> requiredCertificates,
                                    List<String> preferredCertificates,
                                    JobCategory jobCategory) {
        this(requiredSkills, List.of(), preferredSkills, minYears, maxYears, newGraduateAllowed,
                requiredCertificates, preferredCertificates, jobCategory);
    }

    // 점수 계산 단위로 본 필수 기술 목록. 단일 필수 기술은 원소 1개짜리 그룹, 택1 그룹은 그대로 하나의 그룹이다.
    // 그룹 중 하나라도 보유하면 그 단위 하나를 충족한 것으로 본다.
    public List<List<String>> requiredSkillGroups() {
        return Stream.concat(
                requiredSkills.stream().map(List::of),
                requiredAnyOf.stream()
        ).toList();
    }

    public boolean hasRequiredSkills() {
        return !requiredSkills.isEmpty() || !requiredAnyOf.isEmpty();
    }
}
