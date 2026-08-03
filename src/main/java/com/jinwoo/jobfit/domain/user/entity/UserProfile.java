package com.jinwoo.jobfit.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType;

    public UserProfile(String desiredJob,
                        CareerLevel careerLevel,
                        Integer yearsOfExperience,
                        String desiredLocation,
                        EmploymentType employmentType) {
        this.desiredJob = desiredJob;
        this.careerLevel = careerLevel;
        this.yearsOfExperience = yearsOfExperience;
        this.desiredLocation = desiredLocation;
        this.employmentType = employmentType;
    }
}
