package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.UserCertificate;

import java.time.LocalDate;

public record UserCertificateResponse(
        Long id,
        String certificateName,
        String grade,
        LocalDate acquiredAt
) {
    public static UserCertificateResponse from(UserCertificate entity) {
        return new UserCertificateResponse(
                entity.getId(),
                entity.getCertificateName(),
                entity.getGrade(),
                entity.getAcquiredAt()
        );
    }
}
