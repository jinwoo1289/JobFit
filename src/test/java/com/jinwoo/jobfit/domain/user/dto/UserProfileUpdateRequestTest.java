package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileUpdateRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void normalizedSkills_trimsLowercasesAndRemovesBlankAndDuplicates() {
        UserProfileUpdateRequest request = requestWith(3, Arrays.asList(" Spring ", "spring", "", "  ", null, "JPA", "jpa"));

        assertThat(request.normalizedSkills()).containsExactly("spring", "jpa");
    }

    @Test
    void normalizedSkills_doesNotApplySkillAlias() {
        // 별칭 치환(spring → spring boot)은 매칭 시점에만 적용하고 저장값은 사용자 입력을 보존한다.
        UserProfileUpdateRequest request = requestWith(3, List.of("Spring", "Spring Framework", "API"));

        assertThat(request.normalizedSkills()).containsExactly("spring", "spring framework", "api");
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validator.validate(requestWith(0, List.of("Java")))).isEmpty();
    }

    @Test
    void negativeYearsOfExperience_isRejected() {
        Set<ConstraintViolation<UserProfileUpdateRequest>> violations = validator.validate(requestWith(-1, List.of("Java")));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("yearsOfExperience");
    }

    @Test
    void blankOrEmptySkills_areRejected() {
        assertThat(validator.validate(requestWith(1, List.of(" ", ""))))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("skillsPresent");
        assertThat(validator.validate(requestWith(1, List.of())))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("skillsPresent");
    }

    // 고용형태를 고르지 않으면(null·빈 집합) 무관으로 보고 검증을 통과한다.
    @Test
    void missingEmploymentTypes_isAllowedAsAny() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "백엔드 개발자", CareerLevel.NEW_GRADUATE, 0, null, null, List.of("Java"), null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nullEmploymentTypeElement_isRejected() {
        Set<EmploymentType> withNull = new HashSet<>(Arrays.asList(EmploymentType.FULL_TIME, null));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                "백엔드 개발자", CareerLevel.NEW_GRADUATE, 0, null, withNull, List.of("Java"), null, null);

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .singleElement().asString().startsWith("employmentTypes");
    }

    private UserProfileUpdateRequest requestWith(Integer years, List<String> skills) {
        return new UserProfileUpdateRequest(
                "백엔드 개발자", CareerLevel.NEW_GRADUATE, years, "서울", Set.of(), skills, null, null);
    }
}
