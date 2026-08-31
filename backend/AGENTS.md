# backend/AGENTS.md

Blog.zip 서버 애플리케이션 규칙이다. 루트 `AGENTS.md`를 먼저 따르고, 여기 규칙이 충돌하면 이 문서를 우선한다.

## 기술 기준

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 언어 | Kotlin 2.2+ | `docs/decisions/007-persistence-stack.md` |
| JDK | 21 (Gradle toolchain으로 고정) | 007 |
| Framework | Spring Boot 4.1.x | 007 |
| Build | Gradle (Kotlin DSL) | 007 |
| 지속성 | Spring Data JPA (Hibernate) | 007 |
| 동적 쿼리 | QueryDSL `io.github.openfeign.querydsl` 7.x + KSP | 007 |
| DB | PostgreSQL 18.x (로컬·테스트도 동일) | `001-database-selection.md` |
| Migration | Flyway, `ddl-auto: validate` | `008-schema-migration.md` |
| ID | ULID `char(26)`, 앱에서 생성 | `006-id-strategy.md` |
| 단기 상태 | 인메모리 (Caffeine), refreshToken은 DB | `009-ephemeral-state.md` |
| 테스트 DB | Testcontainers PostgreSQL | 001, 008 |

H2를 사용하지 않는다. 이유는 `docs/decisions/001-database-selection.md` 2장에 있다.

상세 관례는 각 Decision 문서를 기준으로 한다. 여기에는 자주 어기는 것만 적는다.

- 엔티티는 `data class`를 쓰지 않는다. 동등성은 ID로만 판단한다.
- JPA 애노테이션은 필드에 붙인다. KSP codegen은 `@Access(PROPERTY)`를 지원하지 않는다.
- 모든 `@ManyToOne`에 `fetch = LAZY`를 명시한다. 기본값이 EAGER다.
- Blog에 Post 컬렉션을 매핑하지 않는다. Post는 수집으로 계속 늘어난다.
- ULID를 앱에서 할당하므로 `save()`가 `merge()`로 간다. `Persistable` 구현 또는 `persist()` 직접 호출로 대응한다.
- **외부 HTTP 호출을 트랜잭션 안에서 하지 않는다.** 외부 응답을 받은 다음 짧은 트랜잭션에서 저장한다.
- 엔티티를 API 응답으로 그대로 리턴하지 않는다. `feedUrl` 노출을 막는 장치다.
- 이미 머지된 Flyway 파일을 수정하지 않는다. 새 파일로 고친다.

### Boot 4에서 자주 밟는 것

Boot 3 기준 자료를 그대로 따르면 막힌다. 상세는 `docs/decisions/007-persistence-stack.md` 2장.

- Jackson은 `tools.jackson` 3.x다. `com.fasterxml.jackson` 타입은 빈으로 등록되지 않는다.
- Flyway는 `spring-boot-starter-flyway`가 필요하다. `flyway-core`만 넣으면 기동은 되고 마이그레이션만 조용히 실행되지 않는다.
- 엔티티 ID는 `private val` + `Persistable.getId()` 구현이다. public `val id`는 JVM 시그니처 충돌로 컴파일에 실패한다.

## 실행

먼저 PostgreSQL을 띄운다. H2를 쓰지 않으므로 Docker가 전제다.

```bash
cd backend
docker compose up -d
export JWT_SECRET=$(openssl rand -hex 32)
./gradlew bootRun
```

Swagger UI: `http://localhost:8080/swagger-ui.html` (local, dev 프로필만)

## 테스트

```bash
cd backend
./gradlew build
```

통합 테스트는 Testcontainers로 PostgreSQL을 띄운다. Docker가 실행 중이어야 한다.

아직 정해지지 않은 것:

- [ ] Ownership 인증 방법 (`docs/decisions/005-ownership-verification.md`)
- [ ] 패키지 구조 상세 (기능별 domain/application/infra/api 배치는 011에 있다)

## 도메인 경계

핵심 개념은 `docs/PRD.md` 8장을 따른다. 구현에서 다음 경계를 섞지 않는다.

- `User` - 가입 사용자
- `Blog` - 외부 블로그 (Blog.zip 가입 여부와 무관하게 존재)
- `Subscription` - User와 Blog 사이의 개인 구독 관계. 구독자가 지정한 이름을 포함한다.
- `Ownership` - 인증된 소유 관계. Subscription과 별도 모델로 다룬다.
- `Post` - 외부 Blog에서 수집한 게시물

주의할 점:

- Subscription의 친구 이름은 구독자별 값이다. Blog에 저장하지 않는다. (BR-004, BR-005)
- Ownership 없이 Blog에 운영자 User를 연결하지 않는다. (BR-008)
- Blog 소유자가 나중에 Ownership을 인증해도 기존 Subscription을 변경하지 않는다. (BR-007)

## 외부 Feed 처리

- 블로그 URL과 Feed 응답은 신뢰할 수 없는 입력으로 다룬다. 파싱 전 검증한다.
- 수집 실패는 삼키지 않고 상태로 남긴다. (Should Have: Feed 수집 실패 상태 관리)
- 외부 플랫폼 호출은 동시성과 재시도 정책을 명시적으로 제한한다.

## 테스트

- 통합 테스트는 Testcontainers PostgreSQL에 Flyway 마이그레이션을 적용해 실행한다.
- 다음은 실제 DB로 검증한다: 중복 구독, 중복 Post, 동시 Ownership 확인.
- 외부 네트워크에 의존하는 테스트는 고정 Fixture 또는 로컬 스텁을 사용한다.
- Feed와 친구 목록 조회는 쿼리 수를 테스트로 검증한다. N+1을 로그만 보고 넘기지 않는다.
- 고정 sleep으로 비동기 결과를 기다리지 않는다.
- 실행한 테스트 명령과 결과를 PR에 남긴다.
