package com.jinwoo.jobfit.domain.job.controller;

import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공고 등록과 강제 재추출은 LLM 비용이 드므로 공개 배포에서는 jobfit.profile.edit-enabled 플래그로 막는다.
 * 정책은 컨트롤러에 있으므로(시작 시 seed 로딩은 서비스를 직접 호출) HTTP 경로로 검증한다.
 */
class JobDataEditPolicyTest {

    private static final String EXTERNAL_ID = "policy-test-0001";

    private static final String CREATE_BODY = """
            {
              "source": "SARAMIN",
              "externalId": "%s",
              "companyName": "테스트회사",
              "title": "백엔드 개발자",
              "originalUrl": "https://example.com/jobs/1",
              "description": "Spring Boot 백엔드 개발"
            }
            """.formatted(EXTERNAL_ID);

    abstract static class JobFixture {

        @Autowired
        protected MockMvc mockMvc;
        @Autowired
        protected JobPostingRepository jobPostingRepository;
        @Autowired
        protected EntityManager entityManager;

        @MockitoBean
        protected LlmClient llmClient;
    }

    @Nested
    @SpringBootTest(properties = "jobfit.profile.edit-enabled=false")
    @AutoConfigureMockMvc
    @Transactional
    class EditDisabled extends JobFixture {

        @Test
        void create_isForbiddenAndDoesNotSave() throws Exception {
            mockMvc.perform(post("/api/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
            entityManager.flush();
            entityManager.clear();

            assertThat(jobPostingRepository.existsBySourceAndExternalId(JobSource.SARAMIN, EXTERNAL_ID)).isFalse();
        }

        // 존재하는 공고로 호출해, 정책이 없다면 LLM 추출까지 갔을 경로가 막히는지 본다.
        @Test
        void reExtract_isForbiddenAndDoesNotCallLlm() throws Exception {
            Long jobId = jobPostingRepository.save(new JobPostingCreateRequest(
                    JobSource.SARAMIN, EXTERNAL_ID, "테스트회사", "백엔드 개발자", "https://example.com/jobs/1",
                    null, null, null, null, "Spring Boot 백엔드 개발", null, null).toEntity()).getId();
            entityManager.flush();
            entityManager.clear();

            mockMvc.perform(post("/api/jobs/{id}/re-extract", jobId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));

            verifyNoInteractions(llmClient);
        }
    }

    @Nested
    @SpringBootTest(properties = "jobfit.profile.edit-enabled=true")
    @AutoConfigureMockMvc
    @Transactional
    class EditEnabled extends JobFixture {

        @Test
        void create_isAllowed() throws Exception {
            mockMvc.perform(post("/api/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isCreated());
        }

        // 404는 정책을 통과해 서비스까지 도달했다는 뜻이다.
        @Test
        void reExtract_passesPolicyAndReturnsNotFoundForUnknownJob() throws Exception {
            mockMvc.perform(post("/api/jobs/{id}/re-extract", Long.MAX_VALUE))
                    .andExpect(status().isNotFound());
        }
    }
}
