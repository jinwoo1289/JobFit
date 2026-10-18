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

        double actual = calculator.calculate(certificates, List.of("OPIC"), List.of());

        assertThat(actual).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculate_leadingTrailingWhitespaceDiffers_stillMatches() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "  opic  ", "IH", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC"), List.of());

        assertThat(actual).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculate_noMatchingCertificate_scoresZero() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "TOEIC", "900", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC"), List.of());

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

        double actual = calculator.calculate(certificates, List.of("OPIC", "TOEIC Speaking"), List.of());

        assertThat(actual).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculate_multipleRequiredCertificatesNoneHeld_scoresZero() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "TOEIC", "900", null)
        );

        double actual = calculator.calculate(certificates, List.of("OPIC", "TOEIC Speaking"), List.of());

        assertThat(actual).isEqualTo(0.0);
    }

    @Test
    void calculate_noRequiredOrPreferredCertificates_scoresZero() {
        double actual = calculator.calculate(List.of(), List.of(), List.of());

        assertThat(actual).isEqualTo(0.0);
    }

    // 셀트리온(55112954) [우대사항]: "정보처리기사, 클라우드(AWS, Azure) 자격증, Red Hat 계열(RHCSA, RHCE) 또는
    // 리눅스 자격증" — "필수" 표현이 없어 requiredCertificates는 비어 있고 preferredCertificates에만 담기는 경우.
    // 필수 자격증 요구가 없어도 우대 자격증을 보유하면 0점이 아닌 부분 점수를 받아야 한다.
    @Test
    void calculate_celltrion_noRequiredCertificatesButHoldsPreferredCertificate_scoresPartialMarks() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "정보처리기사", null, null)
        );

        double actual = calculator.calculate(
                certificates,
                List.of(),
                List.of("정보처리기사", "AWS", "Azure", "RHCSA", "RHCE")
        );

        assertThat(actual).isCloseTo(60.0, within(0.001));
    }

    // 셀트리온 우대 자격증을 하나도 보유하지 않으면 0점이다.
    @Test
    void calculate_celltrion_holdsNoneOfThePreferredCertificates_scoresZero() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "TOEIC", "900", null)
        );

        double actual = calculator.calculate(
                certificates,
                List.of(),
                List.of("정보처리기사", "AWS", "Azure", "RHCSA", "RHCE")
        );

        assertThat(actual).isEqualTo(0.0);
    }

    // 필수 자격증을 충족하면, 우대 자격증 보유 여부와 무관하게 만점(우대로 점수가 깎이지 않음)을 받는다.
    @Test
    void calculate_requiredCertificateHeld_takesPrecedenceOverPreferredMatch() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "OPIC", "IH", null)
        );

        double actual = calculator.calculate(
                certificates,
                List.of("OPIC"),
                List.of("정보처리기사")
        );

        assertThat(actual).isCloseTo(100.0, within(0.001));
    }

    // 필수 자격증이 있지만 미보유이고, 우대 자격증만 보유한 경우 부분 점수로 떨어진다.
    @Test
    void calculate_requiredCertificateNotHeldButPreferredHeld_fallsBackToPartialMarks() {
        UserProfile profile = profile();
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "정보처리기사", null, null)
        );

        double actual = calculator.calculate(
                certificates,
                List.of("OPIC"),
                List.of("정보처리기사")
        );

        assertThat(actual).isCloseTo(60.0, within(0.001));
    }

    private UserProfile profile() {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
    }
}
