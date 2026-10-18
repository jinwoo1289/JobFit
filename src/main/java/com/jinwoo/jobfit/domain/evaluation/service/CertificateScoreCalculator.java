package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CertificateScoreCalculator {

    private static final double REQUIRED_MATCH_SCORE = 100;
    private static final double PREFERRED_MATCH_SCORE = 60;
    private static final double NO_MATCH_SCORE = 0;

    // 공고의 자격증 요구는 대부분 "A 또는 B" 형태이므로, 하나라도 보유하면 충족으로 본다.
    // 필수 자격증을 하나라도 보유하면 만점, 필수 충족은 없지만 우대 자격증을 보유하면 부분 점수를 부여한다.
    public double calculate(List<UserCertificate> certificates, List<String> requiredCertificates, List<String> preferredCertificates) {
        if (requiredCertificates.isEmpty() && preferredCertificates.isEmpty()) {
            return NO_MATCH_SCORE;
        }

        Set<String> userCertificateNames = certificates.stream()
                .map(UserCertificate::getCertificateName)
                .map(this::normalize)
                .collect(Collectors.toSet());

        if (hasAnyMatch(requiredCertificates, userCertificateNames)) {
            return REQUIRED_MATCH_SCORE;
        }
        return hasAnyMatch(preferredCertificates, userCertificateNames) ? PREFERRED_MATCH_SCORE : NO_MATCH_SCORE;
    }

    private boolean hasAnyMatch(List<String> targetCertificates, Set<String> userCertificateNames) {
        return targetCertificates.stream()
                .map(this::normalize)
                .anyMatch(userCertificateNames::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
