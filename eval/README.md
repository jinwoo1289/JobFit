# 요건 추출 정확도 평가

LLM이 채용 공고에서 뽑은 필수/우대 기술이 사람이 라벨링한 정답과 얼마나 맞는지 측정한다.

## 파일

| 파일 | 내용 |
|---|---|
| `ground_truth.json` | 공고 31개 정답 라벨 |
| `labeling_rules.md` | 라벨링 기준 |
| `postings.json` | 공고 원문 (오류 원인 분류에 사용) |
| `vocab.json` | 채점용 별칭, 개념어, 자격증 패턴 |
| `score.py` | 채점과 오류 원인 분류 |
| `predictions/vN.json` | 버전별 추출 결과 |
| `results/vN.md` | 버전별 리포트, `vN_errors.csv`는 오류 목록 |

## 실행

```bash
# 1. 추출 (캐시 사용 안 함, LLM 호출 31회)
./gradlew bootRun --args='--spring.profiles.active=local,eval --eval.output=eval/predictions/v0.json'

# 2. 채점
python eval/score.py eval/predictions/v0.json             # 후처리 후 (실제 저장되는 결과)
python eval/score.py eval/predictions/v0.json --stage raw # 후처리 전 LLM 원본

# 3. 개선 후 비교
python eval/score.py eval/predictions/v1.json --compare eval/predictions/v0.json
```

## 지표

- **필수+우대 통합**: 필수/우대 구분 없이 기술을 맞췄는지 본다. 매칭에 가장 직접적인 지표다.
- **필수 / 우대**: 각 레벨별로 따로 계산한다. 레벨을 반대로 넣으면 양쪽에서 각각 틀린 것으로 잡힌다.
- **레벨 정확도**: 맞춘 기술 중에서 필수/우대까지 맞은 비율이다.
- 모든 지표는 전체 31개와 ambiguous 4개를 뺀 27개 기준으로 함께 낸다.
- 표기 차이(Spring Framework와 Spring 등)는 `vocab.json` 별칭으로 맞춘 뒤 비교한다. 별칭 없이 채점한 F1도 같이 출력한다.

## 오류 원인

| 유형 | 원인 | 고칠 곳 |
|---|---|---|
| FP | CONCEPT_WORD | 개념어 필터 |
| FP | CERTIFICATE | 프롬프트 (자격증은 별도 필드) |
| FP | KEYWORDS_LEAK | 입력에서 keywords 필드 제외 |
| FP | NOT_IN_TEXT | 환각이거나 `vocab.json` 별칭 누락. 먼저 별칭을 확인할 것 |
| FP | RULE_EXCLUDED | 프롬프트와 라벨링 규칙 불일치 (학습 가능, 예시 스택 등) |
| FN | MISSED_REQ_SECTION / MISSED_PREF_SECTION | 프롬프트 |
| FN | MISSED_DUTY_STACK | 프롬프트가 주요업무와 기술스택 섹션을 읽게 할 것 |
| FN | MISSED_DERIVED | RDBMS→SQL 같은 매핑 규칙 |
| SWAP | REQ_AS_PREF / PREF_AS_REQ | 헤더 판정 규칙 |

원인 분류는 자동 휴리스틱이다. 리포트를 확정하기 전에 `results/vN_errors.csv`를 한 번 훑어볼 것.
