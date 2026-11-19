# 요건 추출 정확도 평가

LLM이 채용 공고에서 뽑은 필수/우대 기술이 정답셋과 얼마나 맞는지 측정한다.

> **정답셋을 만든 방법과 한계**: 라벨링 규칙(`labeling_rules.md`)은 직접 설계했다. 초안 라벨은 추출에 쓰는 gpt-4o와 다른 LLM(Claude)으로 만들었고, 판단이 갈리는 항목은 직접 결정했다(아래 판단 기록). 31건 전수 검수는 아직 하지 않았으므로, 정답 오류가 점수에 섞여 있을 수 있다. 검수 후 정답이 바뀌면 같은 predictions로 다시 채점하고 변경 이유를 판단 기록에 남긴다.

## 측정 이력

| 버전 | 변경 | 통합 F1 | 정밀도 | 재현율 | 필수 F1 | 우대 F1 | 레벨 정확도 | 오탐 | 누락 | 리포트 |
|---|---|---|---|---|---|---|---|---|---|---|
| v0 | 기준선 (prompt v6, gpt-4o) | 0.884 | 0.886 | 0.882 | 0.727 | 0.801 | 0.871 | 27 | 28 | [v0](results/v0.md) |
| v0 raw | 같은 실행의 후처리 전 원본 | 0.877 | 0.871 | 0.882 | 0.724 | 0.793 | 0.871 | 31 | 28 | [v0_raw](results/v0_raw.md) |
| v0.1 | 개념어 필터 보강 (v0 raw 재처리, LLM 호출 0회) | **0.925** | **0.972** | 0.882 | 0.796 | 0.813 | 0.871 | 6 | 28 | [v0.1](results/v0.1.md) |

수치는 모두 공고 31건 전체 기준이다. ambiguous 4건을 뺀 수치는 각 리포트에 있다.

### v0: 기준선

- 기존 개념어 필터가 거른 오탐은 4건(LLM 2, HTTP, CI/CD)뿐이었다. raw 0.877에서 후처리 후 0.884로 +0.007.
- 오탐 27건 중 21건이 개념어·분야명이었다. REST API(8개 공고), NoSQL, AI Agent, Cloud, API, LLM API, Cache DB·Document DB·Graph DB, Machine Learning, Backend/Fullstack Development가 해당된다.
- 누락 28건 중 20건은 주요업무·기술스택 섹션에만 적힌 기술이었다.
- 필수·우대 구분 오류는 27건이었다. 우대를 필수로 넣은 경우가 16건(대부분 주요업무·기술스택 섹션의 기술), 필수를 우대로 넣은 경우가 11건이고, 후자 중 8건은 ambiguous인 토스페이먼츠다.

### v0.1: 개념어 필터 보강

- 위 개념어 13개를 운영 필터에 추가했다. 필터는 정확히 일치하는 단어만 지우므로 Spring Cloud, FastAPI 같은 기술은 남는다.
- 오탐이 27건에서 6건으로 줄었다. TP 210과 누락 28은 그대로라서, 필터가 실제 기술을 지우지 않았음을 확인했다.
- 남은 오탐 6건: "학습 가능"으로 명시된 기술 3건(인이지), 정답 재검토 대상 2건(카카오페이 Spring, 셀트리온 Azure), Windows 1건.

### 다음: 프롬프트 개선 (v1 예정)

- 주요업무·기술스택 섹션의 기술도 추출하되 우대로 분류
- "학습 가능" 문구가 붙은 기술 제외
- 프롬프트의 "REST API는 예외로 유지" 문구 제거 (필터와 규칙 충돌)

## 판단 기록

평가 기준을 정하면서 내린 결정들이다. 정답이나 기준을 바꾸면 여기에 이유를 남긴다.

- **주요업무·기술스택 섹션의 기술은 우대로 라벨링** (2026-11-17): JobFit은 추천 매칭에 쓰므로 팀이 쓰는 스택도 의미 있는 신호다. 다만 지원자에게 요구한 조건은 아니므로 필수가 아니라 우대로 넣는다.
- **국민은행(id 10) 제외** (2026-11-17): 더미 데이터로 판단했다. 본문이 없는 공고와 DB에 없는 공고 5건도 제외했다.
- **AI Agent를 개념어로 처리** (2026-11-19): 10월에는 필터가 정확히 일치하는 단어만 지우도록 설계하면서 AI Agent 같은 복합어는 남겼다. 확인해 보니 매칭과 점수 계산 어디에서도 AI Agent를 쓰지 않았고, 라벨링 규칙상 개념어라서 운영 필터도 같은 기준으로 맞췄다. 그 결과 링크알파 시드 공고의 기술 점수 기대값이 1/12에서 1/11로 바뀌었다.
- **MSA는 개념어 필터에 넣지 않음**: 기술로 잡혀도 괜찮다고 이전에 결정했다.
- **정답 재검토 대상 (v1 전에 결정)**: 토스페이먼츠(자격요건 헤더지만 "~면 좋음" 문구), RDBMS→SQL 규칙에서 나온 SQL 누락 4건, 카카오페이 "Spring Framework(Spring Boot)", 셀트리온 "Azure Entra ID".

## 파일

| 파일 | 내용 |
|---|---|
| `ground_truth.json` | 공고 31개 정답 라벨 (`jobPostingId`는 로컬 DB 기준) |
| `labeling_rules.md` | 라벨링 기준 |
| `postings.json` | 공고 원문 (오류 원인 분류에 사용) |
| `vocab.json` | 채점용 별칭, 개념어, 자격증 패턴 |
| `score.py` | 채점과 오류 원인 분류 |
| `predictions/vN.json` | 버전별 추출 결과 (`raw`에 후처리 전 원본 포함) |
| `results/vN.md` | 버전별 리포트, `vN_errors.csv`는 오류 목록 |

러너는 `src/main/java/com/jinwoo/jobfit/eval/ExtractionEvalRunner.java`에 있다.

## 실행

```bash
# 1. 추출 (캐시를 읽거나 쓰지 않음, LLM 호출 31회)
./gradlew bootRun --args='--spring.profiles.active=local,eval --eval.output=eval/predictions/v0.json'

# 1-1. 후처리만 바꿨을 때: 기존 raw에 후처리만 다시 적용 (LLM 호출 0회)
./gradlew bootRun --args='--spring.profiles.active=local,eval --eval.reprocess-from=eval/predictions/v0.json --eval.output=eval/predictions/v0.1.json'

# 2. 채점
python eval/score.py eval/predictions/v0.json             # 후처리 후 (실제 저장되는 결과)
python eval/score.py eval/predictions/v0.json --stage raw # 후처리 전 LLM 원본

# 3. 이전 버전과 비교
python eval/score.py eval/predictions/v0.1.json --compare eval/predictions/v0.json
```

- 출력 파일이 이미 있으면 러너가 덮어쓰지 않고 종료한다. 새 버전 이름을 쓸 것.
- 정답의 `jobPostingId`는 로컬 DB 기준이다. Render DB는 id가 달라서 쓸 수 없다.

## 지표

- **필수+우대 통합**: 필수/우대 구분 없이 기술을 맞췄는지 본다. 매칭에 가장 직접적인 지표다.
- **필수 / 우대**: 각 레벨별로 따로 계산한다. 레벨을 반대로 넣으면 양쪽에서 각각 틀린 것으로 잡힌다.
- **레벨 정확도**: 맞춘 기술 중에서 필수/우대까지 맞은 비율이다.
- 전체 개수를 합쳐 한 번에 계산하는 마이크로 평균이다. 정답도 예측도 비어 있는 공고는 점수에 영향을 주지 않는다.
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

원인 분류는 자동 휴리스틱이다. 섹션은 `[헤더]`로 시작하는 줄만 인식하므로, 리포트를 확정하기 전에 `results/vN_errors.csv`를 한 번 훑어볼 것.