package com.jinwoo.jobfit.domain.user.controller;

import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import com.jinwoo.jobfit.domain.user.repository.EvaluationWeightRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProfileRepository;
import com.jinwoo.jobfit.domain.user.repository.UserSkillRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerTest {

    private static final String VALID_UPDATE_BODY = """
            {
              "desiredJob": "백엔드 개발자",
              "careerLevel": "EXPERIENCED",
              "yearsOfExperience": 2,
              "desiredLocation": "판교",
              "employmentTypes": ["FULL_TIME", "INTERN"],
              "skills": [" Spring ", "spring", "", "PostgreSQL"],
              "projects": [{"projectName": "JobFit", "description": "공고 적합도 평가", "techStack": ["Spring Boot", "JPA"]}],
              "certificates": [{"certificateName": "TOEIC", "grade": "850", "acquiredAt": "2026-01-10"}]
            }
            """;

    abstract static class ProfileFixture {

        @Autowired
        protected MockMvc mockMvc;
        @Autowired
        protected UserProfileRepository userProfileRepository;
        @Autowired
        protected UserSkillRepository userSkillRepository;
        @Autowired
        protected EvaluationWeightRepository evaluationWeightRepository;
        @Autowired
        protected EntityManager entityManager;

        protected Long profileId;

        // 서비스의 create는 수정 허용 플래그의 영향을 받으므로 저장소로 직접 만든다.
        @BeforeEach
        void createProfile() {
            UserProfile profile = userProfileRepository.save(
                    new UserProfile("기존 직무", CareerLevel.NEW_GRADUATE, 0, "서울", Set.of()));
            userSkillRepository.save(new UserSkill(profile, "Java"));
            evaluationWeightRepository.save(new EvaluationWeight(profile, 40, 30, 20, 10));
            entityManager.flush();
            entityManager.clear();
            profileId = profile.getId();
        }
    }

    @Nested
    @SpringBootTest(properties = "jobfit.profile.edit-enabled=true")
    @AutoConfigureMockMvc
    @Transactional
    class EditEnabled extends ProfileFixture {

        @Test
        void settings_reportsEditEnabled() throws Exception {
            mockMvc.perform(get("/api/users/profile/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.editEnabled").value(true));
        }

        @Test
        void update_savesNormalizedSkillsAndKeepsWeights() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.desiredJob").value("백엔드 개발자"))
                    .andExpect(jsonPath("$.weights.skillWeight").value(40));
            entityManager.flush();
            entityManager.clear();

            assertThat(userSkillRepository.findByUserProfileId(profileId))
                    .extracting(UserSkill::getSkillName)
                    .containsExactly("spring", "postgresql");

            mockMvc.perform(get("/api/users/profile/{id}", profileId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.yearsOfExperience").value(2))
                    .andExpect(jsonPath("$.employmentTypes.length()").value(2))
                    .andExpect(jsonPath("$.employmentTypes[0]").value("FULL_TIME"))
                    .andExpect(jsonPath("$.employmentTypes[1]").value("INTERN"))
                    .andExpect(jsonPath("$.projects[0].techStack[0]").value("spring boot"))
                    .andExpect(jsonPath("$.certificates[0].grade").value("850"))
                    .andExpect(jsonPath("$.weights.certificateWeight").value(10));
        }

        @Test
        void update_rejectsNegativeYearsOfExperience() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY.replace("\"yearsOfExperience\": 2", "\"yearsOfExperience\": -1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("yearsOfExperience"));
        }

        @Test
        void update_rejectsUnknownEmploymentType() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY.replace("\"FULL_TIME\"", "\"PART_TIME\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        // ANY는 제거됐다. 무관은 빈 배열로 표현한다.
        @Test
        void update_rejectsRemovedAnyEmploymentType() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY.replace("[\"FULL_TIME\", \"INTERN\"]", "[\"ANY\"]")))
                    .andExpect(status().isBadRequest());
        }

        // 옛 단일 필드(employmentType)로 보내면 무시돼 조용히 "무관"으로 저장되지 않도록 400으로 거부한다.
        @Test
        void update_rejectsLegacyEmploymentTypeField() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY.replace(
                                    "\"employmentTypes\": [\"FULL_TIME\", \"INTERN\"]", "\"employmentType\": \"FULL_TIME\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
            entityManager.flush();
            entityManager.clear();

            assertThat(userProfileRepository.findById(profileId)).get()
                    .extracting(UserProfile::getDesiredJob).isEqualTo("기존 직무");
        }

        @Test
        void update_rejectsUnknownField() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY.replace("\"desiredJob\"", "\"nickname\": \"x\", \"desiredJob\"")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void update_withEmptyEmploymentTypes_savesAsAny() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY.replace("[\"FULL_TIME\", \"INTERN\"]", "[]")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.employmentTypes").isEmpty());
        }

        @Test
        void update_rejectsBlankOnlySkills() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY.replace("[\" Spring \", \"spring\", \"\", \"PostgreSQL\"]", "[\" \", \"\"]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("skillsPresent"));
        }

        @Test
        void update_returnsNotFoundForUnknownProfile() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", Long.MAX_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest(properties = "jobfit.profile.edit-enabled=false")
    @AutoConfigureMockMvc
    @Transactional
    class EditDisabled extends ProfileFixture {

        @Test
        void settings_reportsEditDisabled() throws Exception {
            mockMvc.perform(get("/api/users/profile/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.editEnabled").value(false));
        }

        @Test
        void update_isForbiddenAndDoesNotChangeProfile() throws Exception {
            mockMvc.perform(put("/api/users/profile/{id}", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_UPDATE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
            entityManager.flush();
            entityManager.clear();

            assertThat(userProfileRepository.findById(profileId)).get()
                    .extracting(UserProfile::getDesiredJob).isEqualTo("기존 직무");
            assertThat(userSkillRepository.findByUserProfileId(profileId))
                    .extracting(UserSkill::getSkillName).containsExactly("Java");
        }

        @Test
        void createAndDelete_areForbidden() throws Exception {
            mockMvc.perform(post("/api/users/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"desiredJob": "백엔드", "careerLevel": "NEW_GRADUATE", "employmentTypes": [],
                                     "skills": [{"skillName": "Java"}],
                                     "weights": {"skillWeight": 40, "experienceWeight": 30, "preferenceWeight": 20, "certificateWeight": 10}}
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/users/profile/{id}", profileId))
                    .andExpect(status().isForbidden());
            entityManager.flush();
            entityManager.clear();
            assertThat(userProfileRepository.findById(profileId)).isPresent();
        }

        @Test
        void readAndWeightUpdate_remainAvailable() throws Exception {
            mockMvc.perform(get("/api/users/profile/{id}", profileId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.skills[0].skillName").value("Java"));

            mockMvc.perform(put("/api/users/profile/{id}/weights", profileId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"skillWeight": 25, "experienceWeight": 25, "preferenceWeight": 25, "certificateWeight": 25}
                                    """))
                    .andExpect(status().isOk());
        }
    }
}
