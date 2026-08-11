package com.jinwoo.jobfit.domain.user.service;

import com.jinwoo.jobfit.domain.user.dto.EvaluationWeightRequest;
import com.jinwoo.jobfit.domain.user.dto.UserCertificateRequest;
import com.jinwoo.jobfit.domain.user.dto.UserProfileCreateRequest;
import com.jinwoo.jobfit.domain.user.dto.UserProfileResponse;
import com.jinwoo.jobfit.domain.user.dto.UserProjectRequest;
import com.jinwoo.jobfit.domain.user.dto.UserSkillRequest;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.repository.EvaluationWeightRepository;
import com.jinwoo.jobfit.domain.user.repository.UserCertificateRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProfileRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProjectRepository;
import com.jinwoo.jobfit.domain.user.repository.UserSkillRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserProfileServiceTest {

    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private UserSkillRepository userSkillRepository;
    @Autowired
    private UserProjectRepository userProjectRepository;
    @Autowired
    private UserCertificateRepository userCertificateRepository;
    @Autowired
    private EvaluationWeightRepository evaluationWeightRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void delete_removesProfileAndAllAssociatedData() {
        UserProfileResponse created = userProfileService.create(newCreateRequest());
        Long id = created.id();
        entityManager.flush();
        entityManager.clear();

        assertThat(userSkillRepository.findByUserProfileId(id)).isNotEmpty();
        assertThat(userProjectRepository.findByUserProfileId(id)).isNotEmpty();
        assertThat(userCertificateRepository.findByUserProfileId(id)).isNotEmpty();
        assertThat(evaluationWeightRepository.findByUserProfileId(id)).isPresent();

        userProfileService.delete(id);
        entityManager.flush();
        entityManager.clear();

        assertThat(userProfileRepository.findById(id)).isEmpty();
        assertThat(userSkillRepository.findByUserProfileId(id)).isEmpty();
        assertThat(userProjectRepository.findByUserProfileId(id)).isEmpty();
        assertThat(userCertificateRepository.findByUserProfileId(id)).isEmpty();
        assertThat(evaluationWeightRepository.findByUserProfileId(id)).isEmpty();
    }

    private UserProfileCreateRequest newCreateRequest() {
        return new UserProfileCreateRequest(
                "백엔드 개발자",
                CareerLevel.NEW_GRADUATE,
                null,
                "서울",
                EmploymentType.FULL_TIME,
                List.of(new UserSkillRequest("Java")),
                List.of(new UserProjectRequest("JobFit", "채용 적합도 서비스", List.of("spring", "jpa"))),
                List.of(new UserCertificateRequest("OPIc", "IH", LocalDate.of(2024, 3, 15))),
                new EvaluationWeightRequest(40, 30, 20, 10)
        );
    }
}
