package com.jinwoo.jobfit.domain.job.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.exception.DuplicateJobPostingException;
import com.jinwoo.jobfit.domain.job.service.JobPostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingDataLoader implements CommandLineRunner {

    private final JobPostingService jobPostingService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        ClassPathResource resource = new ClassPathResource("seed/jobs.json");

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            int saved = 0;
            int duplicated = 0;
            int failed = 0;

            for (int i = 0; i < root.size(); i++) {
                JsonNode node = root.get(i);
                try {
                    JobPostingCreateRequest job =
                            objectMapper.treeToValue(node, JobPostingCreateRequest.class);
                    jobPostingService.create(job);
                    saved++;
                } catch (DuplicateJobPostingException e) {
                    duplicated++;
                    log.info("이미 존재하는 공고: index={}, externalId={}",
                            i, node.path("externalId").asText());
                } catch (Exception e) {
                    failed++;
                    log.error("공고 로딩 실패: index={}, externalId={}, 원인={}",
                            i, node.path("externalId").asText(), e.getMessage(), e);
                }
            }

            log.info("채용공고 데이터 로딩 완료: 전체 {}개 (저장 {}, 중복 {}, 실패 {})",
                    root.size(), saved, duplicated, failed);
        } catch (Exception e) {
            log.error("채용공고 시드 파일 로딩 실패: {}", e.getMessage(), e);
        }
    }
}