package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.client.OpenAiLlmClient;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * seed/jobs.json 8건에 대해 실제 OpenAI API를 호출한다.
 * 과금이 발생하므로 평소에는 비활성화하고, 필요할 때만 @Disabled를 해제해서 수동으로 돌린다.
 * .env의 OPENAI_API_KEY가 spring.config.import로 자동 로드되어 별도 환경변수 설정이 필요 없다.
 * JobRequirementExtractor가 job_requirement_extractions에 결과를 캐시하므로, seed 공고를 먼저
 * job_postings에 저장(또는 기존 행 재사용)해 실제 FK를 가진 상태로 extract()를 호출한다.
 * 재실행 시 이미 캐시된 공고는 LLM을 다시 호출하지 않고 저장된 결과를 재사용한다.
 */
@SpringBootTest
//@Disabled("실제 OpenAI API를 호출하는 수동 테스트")
class JobRequirementExtractorIntegrationTest {

    @Autowired
    private OpenAiLlmClient openAiLlmClient;

    @Autowired
    private JobRequirementExtractor extractor;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void extract_seedJobPostings() throws Exception {
        assertThat(openAiLlmClient).isNotNull();

        List<JobPosting> jobPostings = loadOrPersistSeedJobPostings();
        assertThat(jobPostings).hasSize(8);

        for (JobPosting jobPosting : jobPostings) {
            JobRequirementExtraction extraction = extractor.extract(jobPosting);

            System.out.printf(
                    "[%s] requiredSkills=%s, preferredSkills=%s, minYears=%s, maxYears=%s, newGraduateAllowed=%s, requiredCertificates=%s, jobCategory=%s%n",
                    jobPosting.getTitle(),
                    extraction.requiredSkills(),
                    extraction.preferredSkills(),
                    extraction.minYears(),
                    extraction.maxYears(),
                    extraction.newGraduateAllowed(),
                    extraction.requiredCertificates(),
                    extraction.jobCategory()
            );

            assertThat(extraction).isNotNull();
        }
    }

    private List<JobPosting> loadOrPersistSeedJobPostings() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("seed/jobs.json")) {
            List<JobPostingCreateRequest> requests = objectMapper.readValue(
                    inputStream,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, JobPostingCreateRequest.class)
            );
            return requests.stream().map(this::findOrSave).toList();
        }
    }

    private JobPosting findOrSave(JobPostingCreateRequest request) {
        return jobPostingRepository.findBySourceAndExternalId(request.source(), request.externalId())
                .orElseGet(() -> jobPostingRepository.save(request.toEntity()));
    }
}
