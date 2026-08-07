package com.jinwoo.jobfit.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_skills")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    public UserSkill(UserProfile userProfile, String skillName) {
        this.userProfile = userProfile;
        this.skillName = skillName;
    }
}
