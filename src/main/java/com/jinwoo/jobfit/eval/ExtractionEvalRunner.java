package com.jinwoo.jobfit.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jinwoo.jobfit.domain.evaluation.client.OpenAiLlmClient;
import com.jinwoo.jobfit.domain.evaluation.service.JobRequirementExtractor;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;

// JobRequirementExtractor가 주입받는 것과 같은 Jackson 3 ObjectMapper 빈을 쓴다.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * 요건 추출 평가 러너. eval 프로필에서만 뜨고, 정답셋 공고를 캐시 없이 추출해 predictions 파일로 저장한 뒤 종료한다.
 *
 * 실행 (레포 루트):
 *   ./gradlew bootRun --args='--spring.profiles.active=local,eval --eval.output=eval/predictions/v0.json'
 *
 * 지켜야 할 것:
 *   1. 추출 캐시를 읽지도 쓰지도 않는다. 캐시 경로(extract/reExtract)가 아니라 LLM만 부르는 requestRawExtraction을 호출한다.
 *      (캐시에 쓰면 평가 실행이 운영 데이터를 바꾼다.)
 *   2. 후처리(개념어 필터, IT 가드) 전 결과를 raw에, 후처리 후 결과를 required/preferred에 저장한다.
 *      그래야 오류가 프롬프트 문제인지 후처리 문제인지 나눌 수 있다.
 *   3. 실패한 공고는 건너뛰지 말고 error를 남긴다. 채점에서 전부 누락으로 계산된다.
 *   4. SkillAliasNormalizer는 매칭용이라 적용하지 않는다. 표기 차이는 score.py가 vocab.json 별칭으로 맞춘다.
 */
// ApplicationRunner로 두면 @Order 없는 다른 러너(시드 로딩 등)와 순서가 보장되지 않는다.
// ApplicationReadyEvent는 모든 Application/CommandLineRunner가 끝난 뒤 발행되므로, 시드 로딩 이후에 돌고 System.exit이 다른 러너를 끊지 않는다.
@Component
@Profile("eval")
public class ExtractionEvalRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalRunner.class);

    private final JobPostingRepository jobPostingRepository;
    private final JobRequirementExtractor extractor;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;

    @Value("${eval.ground-truth:eval/ground_truth.json}")
    private String groundTruthPath;

    @Value("${eval.output:eval/predictions/v0.json}")
    private String outputPath;

    // 프롬프트 버전과 모델은 설정 키가 아니라 코드 상수라 @Value 대신 상수를 직접 기록한다.
    private final int promptVersion = JobRequirementExtractor.EXTRACTION_PROMPT_VERSION;
    private final String model = OpenAiLlmClient.MODEL;

    public ExtractionEvalRunner(JobPostingRepository jobPostingRepository,
                                JobRequirementExtractor extractor,
                                ObjectMapper objectMapper,
                                ApplicationContext context) {
        this.jobPostingRepository = jobPostingRepository;
        this.extractor = extractor;
        this.objectMapper = objectMapper;
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() throws Exception {
        Path out = Path.of(outputPath);
        if (Files.exists(out)) {
            throw new IllegalStateException(out + " 이미 있음. 덮어쓰지 않으려면 버전 이름을 바꿀 것");
        }

        JsonNode gt = objectMapper.readTree(Files.readString(Path.of(groundTruthPath)));
        List<Map<String, Object>> items = new ArrayList<>();
        int failed = 0;

        for (JsonNode g : gt) {
            long id = g.get("jobPostingId").asLong();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobPostingId", id);
            long start = System.currentTimeMillis();
            try {
                JobPosting posting = jobPostingRepository.findById(id)
                        .orElseThrow(() -> new IllegalStateException("공고 없음: " + id));

                JobRequirementExtraction raw = extractor.requestRawExtraction(posting);
                JobRequirementExtraction processed = extractor.postProcess(posting, raw);

                item.put("required", requiredForScoring(processed));
                item.put("preferred", processed.preferredSkills());
                item.put("requiredAnyOf", processed.requiredAnyOf());
                item.put("raw", Map.of(
                        "required", requiredForScoring(raw),
                        "preferred", raw.preferredSkills(),
                        "requiredAnyOf", raw.requiredAnyOf()));
            } catch (Exception e) {
                failed++;
                item.put("required", List.of());
                item.put("preferred", List.of());
                item.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                log.warn("[eval] id={} 추출 실패", id, e);
            }
            item.put("latencyMs", System.currentTimeMillis() - start);
            items.add(item);
            log.info("[eval] {}/{} id={} {}ms", items.size(), gt.size(), id, item.get("latencyMs"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("promptVersion", promptVersion);
        result.put("model", model);
        result.put("runAt", OffsetDateTime.now().toString());
        result.put("postings", items.size());
        result.put("failed", failed);
        result.put("items", items);

        Path parent = out.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Files.writeString(out, objectMapper.writer(SerializationFeature.INDENT_OUTPUT).writeValueAsString(result));
        log.info("[eval] 저장 완료: {} (실패 {}건)", out, failed);

        int exitCode = failed == 0 ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    // 라벨링 규칙상 "A 또는 B"는 A·B 모두 필수로 라벨링하므로, 택1 그룹(requiredAnyOf)의 기술도 필수로 펼쳐서 채점한다.
    private static List<String> requiredForScoring(JobRequirementExtraction extraction) {
        return Stream.concat(
                extraction.requiredSkills().stream(),
                extraction.requiredAnyOf().stream().flatMap(List::stream)
        ).distinct().toList();
    }
}
