# Labeling rules (ground_truth.json)

1. **기술만 넣기**: 언어, 프레임워크, DB, 미들웨어, 클라우드, OS, 이름이 있는 개발 도구(Git, JIRA, Claude Code 등)만 넣는다. 다음은 제외한다.
   - 자격증
   - 업무 도메인(ERP, MTS, BSS)
   - 개념어(REST API, HTTP, MSA, CI/CD, TDD, NoSQL, LLM, VectorDB)
   - 일반 협업 도구(Slack, Notion)
2. **"학습 가능" 기술은 제외**한다. 공고에 "학습 가능", "업무에 따라 학습" 같은 문구가 붙은 기술이 해당된다.
3. **필수/우대 구분**: JobFit은 추천 매칭에 쓰므로 팀이 쓰는 스택도 정답에 넣는다.
   - [자격요건], [필수], [필요기술] 헤더에 있는 기술은 필수
   - [우대] 헤더에 있는 기술은 우대
   - 주요업무, 담당업무, 기술스택, 기술환경 섹션에만 있는 기술은 우대
   - 같은 기술이 여러 곳에 나오면 필수를 우선한다.
   - "A 또는 B", "A, B 중 하나 이상"은 A와 B 모두 넣는다.
   - "어떤 스택이든 무관" 뒤에 붙은 예시 스택은 필수에서 제외한다.
4. **이름은 정규형으로** 쓴다. Spring Framework는 Spring, Golang은 Go, Vue는 Vue.js, K8s는 Kubernetes, Git(Github)와 Github는 Git, Copilot은 GitHub Copilot으로 적는다. AWS 하위 서비스는 AWS로 묶는다. C/C++는 C와 C++로 나눈다.
5. **개념어 처리**: RDBMS, 관계형 DB, RDB, 쿼리 작성은 SQL로 통합한다. "PostgreSQL 등 RDBMS"는 PostgreSQL과 SQL을 둘 다 넣는다. 나머지 개념어는 제외한다.
6. **평가 대상에서 빼는 공고**: 본문이 없는 공고와 더미 데이터는 정답 파일에서 뺀다. 국민은행 공고(id 10)가 여기에 해당한다.
