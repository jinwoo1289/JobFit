package com.jinwoo.jobfit.domain.user.entity;

import com.jinwoo.jobfit.domain.user.exception.InvalidEvaluationWeightException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "evaluation_weights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationWeight {

    private static final int TOTAL_WEIGHT = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false, unique = true)
    private UserProfile userProfile;

    @Column(name = "skill_weight", nullable = false)
    private int skillWeight;

    @Column(name = "experience_weight", nullable = false)
    private int experienceWeight;

    @Column(name = "preference_weight", nullable = false)
    private int preferenceWeight;

    @Column(name = "certificate_weight", nullable = false)
    private int certificateWeight;

    public EvaluationWeight(UserProfile userProfile,
                             int skillWeight,
                             int experienceWeight,
                             int preferenceWeight,
                             int certificateWeight) {
        validateTotal(skillWeight, experienceWeight, preferenceWeight, certificateWeight);
        this.userProfile = userProfile;
        this.skillWeight = skillWeight;
        this.experienceWeight = experienceWeight;
        this.preferenceWeight = preferenceWeight;
        this.certificateWeight = certificateWeight;
    }

    private void validateTotal(int skillWeight, int experienceWeight, int preferenceWeight, int certificateWeight) {
        int total = skillWeight + experienceWeight + preferenceWeight + certificateWeight;
        if (total != TOTAL_WEIGHT) {
            throw new InvalidEvaluationWeightException("가중치 합계는 100이어야 합니다. 입력값 합계=" + total);
        }
    }
}
