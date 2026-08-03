# JobFit

사람인 공고를 수집해 사용자 조건·가중치 기반으로 적합도를 계산하고 판단 근거를 제공하는 서비스.

## 기술 스택

- Spring Boot 4 + JPA + PostgreSQL
- Java 21

## 패키지 구조

```
domain/{도메인}/{controller,service,repository,entity,dto,exception}
global/
```

공통 관심사(전역 예외 처리 등)는 `global/` 아래에 둔다.

## 컨벤션

- DTO는 record로 작성하고, `toEntity()` / `from()` 정적 팩토리로 엔티티 변환 책임을 DTO에 둔다.
- 예외는 도메인별 커스텀 예외(`domain/{도메인}/exception`)로 정의하고, `global/exception/GlobalExceptionHandler`에서 상태 코드로 매핑한다.
- 커밋 메시지는 Conventional Commits를 따른다 (`feat:`, `refactor:`, `fix:` 등).
