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
 * 재처리 모드 (LLM 호출 없이 기존 predictions의 raw에 현재 후처리만 다시 적용):
 *   ./gradlew bootRun --args='--spring.profiles.active=local,eval --eval.reprocess-from=eval/predictions/v0.json --eval.output=eval/predictions/v0_reprocessed.json'
 *   후처리(개념어 필터 등)만 바꿨을 때 LLM 출력의 흔들림 없이 효과만 비교하려고 쓴다. raw는 원본 그대로 복사한다.
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

    // 비어 있으면 LLM을 불러 새로 추출하고, 지정하면 그 predictions 파일의 raw에 후처리만 다시 적용한다.
    @Value("${eval.reprocess-from:}")
    private String reprocessFrom;

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

        Map<String, Object> result = reprocessFrom.isBlank() ? extractAll() : reprocessAll(Path.of(reprocessFrom));

        Path parent = out.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Files.writeString(out, objectMapper.writer(SerializationFeature.INDENT_OUTPUT).writeValueAsString(result));
        int failed = (int) result.get("failed");
        log.info("[eval] 저장 완료: {} (실패 {}건)", out, failed);

        int exitCode = failed == 0 ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private Map<String, Object> extractAll() throws Exception {
        JsonNode gt = objectMapper.readTree(Files.readString(Path.of(groundTruthPath)));
        List<Map<String, Object>> items = new ArrayList<>();
        int failed = 0;

        for (JsonNode g : gt) {
            long id = g.get("jobPostingId").asLong();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobPostingId", id);
            long start = System.currentTimeMillis();
            try {
                JobPosting posting = findPosting(id);

                JobRequirementExtraction raw = extractor.requestRawExtraction(posting);
                JobRequirementExtraction processed = extractor.postProcess(posting, raw);

                putProcessed(item, processed);
                item.put("raw", Map.of(
                        "required", requiredForScoring(raw),
                        "preferred", raw.preferredSkills(),
                        "requiredAnyOf", raw.requiredAnyOf()));
            } catch (Exception e) {
                failed++;
                putError(item, e);
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
        return result;
    }

    // raw는 원본 실행의 LLM 출력이므로 promptVersion·model은 현재 상수가 아니라 원본 파일 값을 이어받는다.
    // latencyMs도 원본 LLM 호출 시간을 그대로 둔다. 원본에서 실패(error)했거나 raw가 없는 항목은
    // 재처리할 수 없으므로 error를 남기고 실패로 센다.
    private Map<String, Object> reprocessAll(Path source) throws Exception {
        JsonNode src = objectMapper.readTree(Files.readString(source));
        List<Map<String, Object>> items = new ArrayList<>();
        int failed = 0;

        for (JsonNode s : src.get("items")) {
            long id = s.get("jobPostingId").asLong();
            JsonNode raw = s.get("raw");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobPostingId", id);
            if (s.hasNonNull("error") || raw == null || raw.isNull()) {
                failed++;
                item.put("required", List.of());
                item.put("preferred", List.of());
                item.put("error", s.hasNonNull("error") ? s.get("error").asString() : "원본에 raw 없음");
            } else {
                try {
                    JobRequirementExtraction processed = extractor.postProcess(findPosting(id), toRawExtraction(raw));
                    putProcessed(item, processed);
                    item.put("raw", raw);
                } catch (Exception e) {
                    failed++;
                    putError(item, e);
                    log.warn("[eval] id={} 재처리 실패", id, e);
                }
            }
            if (s.has("latencyMs")) {
                item.put("latencyMs", s.get("latencyMs").asLong());
            }
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("promptVersion", src.get("promptVersion").asInt());
        result.put("model", src.get("model").asString());
        result.put("reprocessedFrom", source.toString().replace('\\', '/'));
        result.put("runAt", OffsetDateTime.now().toString());
        result.put("postings", items.size());
        result.put("failed", failed);
        result.put("items", items);
        log.info("[eval] 재처리 완료: {}건 (원본 {})", items.size(), source);
        return result;
    }

    // predictions의 raw.required는 requiredSkills와 택1 그룹을 펼쳐 합친 목록이라(requiredForScoring),
    // 그룹에 든 기술을 빼서 requiredSkills를 복원한다. 후처리는 그룹과 겹치는 requiredSkills를 어차피 제거하므로 결과 기술 집합은 같다.
    // raw에는 jobCategory·경력·자격증이 없어 비워 둔다. jobCategory가 null이면 IT 가드(OTHER 불신)는 동작하지 않지만
    // 채점 대상인 기술 목록에는 영향이 없다.
    private static JobRequirementExtraction toRawExtraction(JsonNode raw) {
        List<List<String>> anyOf = new ArrayList<>();
        for (JsonNode group : raw.path("requiredAnyOf")) {
            anyOf.add(toStringList(group));
        }
        List<String> grouped = anyOf.stream().flatMap(List::stream).toList();
        List<String> required = toStringList(raw.path("required")).stream()
                .filter(skill -> !grouped.contains(skill))
                .toList();
        return new JobRequirementExtraction(
                required, anyOf, toStringList(raw.path("preferred")),
                null, null, false, List.of(), List.of(), null);
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode v : array) {
            values.add(v.asString());
        }
        return values;
    }

    private JobPosting findPosting(long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("공고 없음: " + id));
    }

    private static void putProcessed(Map<String, Object> item, JobRequirementExtraction processed) {
        item.put("required", requiredForScoring(processed));
        item.put("preferred", processed.preferredSkills());
        item.put("requiredAnyOf", processed.requiredAnyOf());
    }

    private static void putError(Map<String, Object> item, Exception e) {
        item.put("required", List.of());
        item.put("preferred", List.of());
        item.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    // 라벨링 규칙상 "A 또는 B"는 A·B 모두 필수로 라벨링하므로, 택1 그룹(requiredAnyOf)의 기술도 필수로 펼쳐서 채점한다.
    private static List<String> requiredForScoring(JobRequirementExtraction extraction) {
        return Stream.concat(
                extraction.requiredSkills().stream(),
                extraction.requiredAnyOf().stream().flatMap(List::stream)
        ).distinct().toList();
    }
}
