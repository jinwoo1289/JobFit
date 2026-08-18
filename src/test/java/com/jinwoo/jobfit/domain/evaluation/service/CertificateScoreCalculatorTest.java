package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CertificateScoreCalculatorTest {

    private final CertificateScoreCalculator calculator = new CertificateScoreCalculator();

    @Test
    void calculate_ociRequiresOpicAndUserHasOpicWithDifferentCase_matches() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "OPIc", "IH", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC"));

        assertThat(actual).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculate_leadingTrailingWhitespaceDiffers_stillMatches() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "  opic  ", "IH", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC"));

        assertThat(actual).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculate_noMatchingCertificate_scoresZero() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "TOEIC", "900", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC"));

        assertThat(actual).isEqualTo(0.0);
    }

    // OCI정보통신 공고는 "OPIC 또는 TOEIC Speaking 점수 제출"을 요구한다.
    // 자격증 요구는 대부분 OR 관계이므로, 둘 중 하나만 보유해도 만점이어야 한다.
    @Test
    void calculate_ociRequiresOpicOrToeicSpeaking_userHasOnlyOpic_scoresFullMarks() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "OPIc", "IH", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC", "TOEIC Speaking"));

        assertThat(actual).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculate_multipleRequiredCertificatesNoneHeld_scoresZero() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "TOEIC", "900", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC", "TOEIC Speaking"));

        assertThat(actual).isEqualTo(0.0);
    }

    @Test
    void calculate_noRequiredCertificates_scoresZero() {
        double actual = calculator.calculate(List.of(), List.of());

        assertThat(actual).isEqualTo(0.0);
    }

    private UserProfile profile() {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
    }
}
