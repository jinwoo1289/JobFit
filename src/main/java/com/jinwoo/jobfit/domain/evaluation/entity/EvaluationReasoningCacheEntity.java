package com.jinwoo.jobfit.domain.evaluation.entity;

import com.jinwoo.jobfit.domain.evaluation.vo.CheckPointsCacheKey;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LLM이 생성한 checkPoints를 (공고, 프로필) 단위로 저장한다. matchedPoints/gapPoints는 규칙 기반이라 저장하지 않는다.
 * 키의 나머지 값(프로필 해시·추출 해시·근거 프롬프트 버전)이 하나라도 달라지면 다시 생성해 같은 행을 덮어쓴다.
 */
@Entity
@Table(
        name = "evaluation_reasoning_caches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"job_posting_id", "user_profile_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationReasoningCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "user_profile_id", nullable = false)
    private Long userProfileId;

    @Column(name = "profile_hash", nullable = false, length = 64)
    private String profileHash;

    @Column(name = "extraction_hash", nullable = false, length = 64)
    private String extractionHash;

    @Column(name = "reasoning_prompt_version", nullable = false)
    private int reasoningPromptVersion;

    // checkPoints 문장에 쉼표가 들어갈 수 있어 CSV가 아니라 JSON 배열 문자열로 저장한다.
    @Column(name = "check_points_json", nullable = false, length = 4000)
    private String checkPointsJson;

    public EvaluationReasoningCacheEntity(CheckPointsCacheKey key, String checkPointsJson) {
        this.jobPostingId = key.jobPostingId();
        this.userProfileId = key.userProfileId();
        overwrite(key, checkPointsJson);
    }

    public void overwrite(CheckPointsCacheKey key, String checkPointsJson) {
        this.profileHash = key.profileHash();
        this.extractionHash = key.extractionHash();
        this.reasoningPromptVersion = key.reasoningPromptVersion();
        this.checkPointsJson = checkPointsJson;
    }

    public boolean matches(CheckPointsCacheKey key) {
        return profileHash.equals(key.profileHash())
                && extractionHash.equals(key.extractionHash())
                && reasoningPromptVersion == key.reasoningPromptVersion();
    }
}
