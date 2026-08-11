package com.jinwoo.jobfit.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "user_certificates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "certificate_name", nullable = false, length = 200)
    private String certificateName;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "acquired_at")
    private LocalDate acquiredAt;

    public UserCertificate(UserProfile userProfile, String certificateName, String grade, LocalDate acquiredAt) {
        this.userProfile = userProfile;
        this.certificateName = certificateName;
        this.grade = grade;
        this.acquiredAt = acquiredAt;
    }
}
