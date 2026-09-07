package com.jinwoo.jobfit.domain.job.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.exception.DuplicateJobPostingException;
import com.jinwoo.jobfit.domain.job.service.JobPostingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JobPostingDataLoader implements CommandLineRunner {

    private final JobPostingService jobPostingService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {

        ClassPathResource resource =
                new ClassPathResource("seed/jobs.json");

        try (InputStream inputStream = resource.getInputStream()) {

            List<JobPostingCreateRequest> jobs =
                    objectMapper.readValue(
                            inputStream,
                            new TypeReference<List<JobPostingCreateRequest>>() {}
                    );

            for (JobPostingCreateRequest job : jobs) {
                try {
                    jobPostingService.create(job);
                } catch (DuplicateJobPostingException e) {
                    System.out.println(
                            "이미 존재하는 공고: "
                            + job.source() + " / "
                            + job.externalId()
                    );
                }
            }

            System.out.println("채용공고 데이터 로딩 완료: " + jobs.size() + "개");
        }
    }
}