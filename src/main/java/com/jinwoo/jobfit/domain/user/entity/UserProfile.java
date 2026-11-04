package com.jinwoo.jobfit.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "desired_job", nullable = false, length = 200)
    private String desiredJob;

    @Enumerated(EnumType.STRING)
    @Column(name = "career_level", nullable = false, length = 30)
    private CareerLevel careerLevel;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "desired_location", length = 200)
    private String desiredLocation;

    // 희망 고용형태(복수 선택). 비어 있으면 "무관"으로 보고 어떤 공고 고용형태든 통과시킨다.
    @ElementCollection
    @CollectionTable(name = "user_profile_employment_types", joinColumns = @JoinColumn(name = "user_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private Set<EmploymentType> employmentTypes = new HashSet<>();

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserSkill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserProject> projects = new ArrayList<>();

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserCertificate> certificates = new ArrayList<>();

    @OneToOne(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private EvaluationWeight evaluationWeight;

    public UserProfile(String desiredJob,
                        CareerLevel careerLevel,
                        Integer yearsOfExperience,
                        String desiredLocation,
                        Collection<EmploymentType> employmentTypes) {
        this.desiredJob = desiredJob;
        this.careerLevel = careerLevel;
        this.yearsOfExperience = yearsOfExperience;
        this.desiredLocation = desiredLocation;
        replaceEmploymentTypes(employmentTypes);
    }

    public void updateBasicInfo(String desiredJob, CareerLevel careerLevel, Integer yearsOfExperience,
                                String desiredLocation, Collection<EmploymentType> employmentTypes) {
        this.desiredJob = desiredJob;
        this.careerLevel = careerLevel;
        this.yearsOfExperience = yearsOfExperience;
        this.desiredLocation = desiredLocation;
        replaceEmploymentTypes(employmentTypes);
    }

    public boolean acceptsEmploymentType(EmploymentType employmentType) {
        return employmentTypes.isEmpty() || employmentTypes.contains(employmentType);
    }

    // 응답·사유 문구가 매번 같은 순서로 나오도록 enum 선언 순서로 정렬해 돌려준다.
    public List<EmploymentType> getSortedEmploymentTypes() {
        return employmentTypes.stream().sorted(Comparator.naturalOrder()).toList();
    }

    // Hibernate가 관리하는 컬렉션 인스턴스를 유지해야 하므로 새 Set으로 바꾸지 않고 내용만 교체한다.
    private void replaceEmploymentTypes(Collection<EmploymentType> employmentTypes) {
        this.employmentTypes.clear();
        if (employmentTypes != null) {
            employmentTypes.stream().filter(Objects::nonNull).forEach(this.employmentTypes::add);
        }
    }
}
