package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CertificateScoreCalculator {

    private static final double MATCH_SCORE = 100;
    private static final double NO_MATCH_SCORE = 0;

    public double calculate(List<UserCertificate> certificates, List<String> requiredCertificates) {
        if (requiredCertificates.isEmpty()) {
            return NO_MATCH_SCORE;
        }

        Set<String> userCertificateNames = certificates.stream()
                .map(UserCertificate::getCertificateName)
                .map(this::normalize)
                .collect(Collectors.toSet());

        long matchedCount = requiredCertificates.stream()
                .map(this::normalize)
                .filter(userCertificateNames::contains)
                .count();

        return (matchedCount * MATCH_SCORE) / requiredCertificates.size();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
