package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UserCertificateRequest(
        @NotBlank String certificateName,
        String grade,
        LocalDate acquiredAt
) {
    public UserCertificate toEntity(UserProfile userProfile) {
        return new UserCertificate(userProfile, certificateName, grade, acquiredAt);
    }
}
