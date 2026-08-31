# 007. Persistence Stack - Kotlin + Spring Data JPA + QueryDSL

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/` 전체
- 관련 Decision: `001-database-selection.md`, `006-id-strategy.md`, `008-schema-migration.md`, `010-api-response-contract.md`, `011-backend-module-structure.md`

---

## 1. Context

Blog.zip 백엔드는 Kotlin과 Spring으로 구현한다. 데이터 접근에서 두 가지 성격의 쿼리가 필요하다.

- 단순 조회와 저장: Subscription 생성, Ownership 조회, Post upsert
- 조건이 붙는 목록 조회: Feed (`unreadOnly` 필터, 커서 페이지네이션, 안정 정렬),
  친구 목록 (마지막 게시물 시각 정렬, 읽지 않은 수 집계)

두 번째는 문자열 JPQL로 쓰면 조건 조합이 늘어날 때 오타를 컴파일 시점에 못 잡는다.

---

## 2. Decision

**Kotlin + Spring Boot + Spring Data JPA(Hibernate) + QueryDSL을 사용한다.**

### 버전 기준

| 항목 | 버전 | 비고 |
| --- | --- | --- |
| Kotlin | 2.4.10 | QueryDSL 7.6이 이 버전으로 빌드됐다 |
| JDK | 21 (LTS) | Boot 4는 17 이상을 요구한다. 팀 로컬 LTS 기준 |
| Spring Boot | 4.1.1 | Spring Framework 7 기반, Jakarta EE 11 |
| Hibernate | Boot BOM 관리 (7.4.x) | 직접 지정하지 않는다 |
| QueryDSL | `io.github.openfeign.querydsl` 7.6 | 원본 `com.querydsl`은 2021년 5.0.0 이후 정지 |
| KSP | 2.3.11 | QueryDSL 7.6이 참조하는 버전 |
| Build | Gradle (Kotlin DSL) | |

버전 조합의 근거: QueryDSL 7.6의 부모 POM이 `springboot.version=4.1.0`, `hibernate.version=7.4.5.Final`, `kotlin.version=2.4.10`, `ksp.version=2.3.11`로 선언돼 있다. 즉 이 조합은 QueryDSL 쪽에서 이미 정렬된 상태다.

JDK는 Gradle toolchain으로 고정한다. 로컬 기본 JDK에 의존하지 않는다.

### QueryDSL은 OpenFeign fork를 쓴다

원본 `com.querydsl`은 2021년 5.0.0이 마지막 릴리즈다. Jakarta EE와 Hibernate 6 이상 지원은 OpenFeign fork에서만 나온다.

- 의존성 group: `io.github.openfeign.querydsl`
- Jakarta 환경이므로 `jakarta` classifier 계열 아티팩트를 쓴다.

### Q 클래스 생성은 KSP로 한다

- 플러그인: `com.google.devtools.ksp`
- 프로세서: `io.github.openfeign.querydsl:querydsl-ksp-codegen`
- KAPT를 쓰지 않는다. KAPT는 유지보수 모드이고 Kotlin 엔티티에서 KSP가 빌드가 빠르다.
- 생성 경로를 소스셋에 추가해 IDE가 Q 클래스를 인식하게 한다.

```kotlin
kotlin {
    sourceSets.main { kotlin.srcDir("build/generated/ksp/main/kotlin") }
}
```

- 제약: KSP codegen은 `@Access(PROPERTY)`를 지원하지 않는다. **JPA 애노테이션은 필드에 붙인다.**

### Boot 4에서 걸린 것

첫 세팅에서 실제로 막혔던 지점이다. Boot 3 기준 자료를 그대로 따르면 다시 밟는다.

| 항목 | Boot 3 | Boot 4 |
| --- | --- | --- |
| Jackson | `com.fasterxml.jackson` 2.x | **`tools.jackson` 3.x.** Jackson 2 타입은 빈으로 등록되지 않는다 |
| Kotlin 모듈 | `com.fasterxml.jackson.module:jackson-module-kotlin` | `tools.jackson.module:jackson-module-kotlin` |
| Flyway | `org.flywaydb:flyway-core`만으로 자동 설정됨 | **`spring-boot-starter-flyway` 필요.** autoconfiguration이 모듈화되어 core만 넣으면 마이그레이션이 조용히 실행되지 않는다 |

Flyway 쪽이 특히 위험하다. 기동은 성공하고 테이블만 없는 상태가 되므로, 스키마 검증 테스트가 없으면 늦게 발견된다. `SchemaMigrationTest`가 그 안전망이다.

Security 필터에서 에러 응답을 직렬화할 때 `ObjectMapper`를 직접 주입하지 않는다. MVC가 쓰는 `HttpMessageConverter`를 재사용해 Jackson 버전에 묶이지 않게 한다.

### Kotlin 엔티티 규칙

- ID는 상위 타입에서 `private val`로 두고 `Persistable.getId()`를 구현한다.
  `val id`를 public으로 두면 Kotlin이 생성하는 getter와 `getId()` 구현이 JVM 시그니처 충돌을 일으켜 컴파일에 실패한다.
- 엔티티는 `class`로 쓴다. `data class`를 쓰지 않는다.
  `equals`/`hashCode`가 모든 프로퍼티로 생성되면 지연 로딩과 컬렉션 동작이 깨진다.
  동등성은 ID로만 판단한다.
- 프로퍼티는 기본적으로 `private set` 또는 불변으로 두고, 변경은 의도가 드러나는 메서드로 한다.
  예: `Subscription.rename(newName)`
- `kotlin("plugin.jpa")`를 적용해 no-arg 생성자를 만든다.
- `kotlin("plugin.spring")`으로 `open` 처리를 맡긴다.
- 연관 관계는 단방향을 우선한다. 양방향은 실제로 양쪽에서 탐색할 때만 만든다.
- 모든 `@ManyToOne`은 `fetch = LAZY`로 명시한다. 기본값이 EAGER다.
- 컬렉션 매핑(`@OneToMany`)은 필요할 때만 만든다. Blog에 Post 컬렉션을 두지 않는다.
  Post는 수집으로 계속 늘어나므로 컬렉션으로 다루면 안 된다.

### 계층 구조

단일 Gradle 모듈에서 기능 우선 패키지로 계층을 나눈다. 상세는 `011-backend-module-structure.md`에 있다.

```text
domain      엔티티, 값 객체, 도메인 규칙 (Spring/JPA 외 의존 최소)
application 유스케이스 서비스, 트랜잭션 경계
infra       Repository 구현, QueryDSL 조회, 외부 Blog 호출
api         Controller, Request/Response DTO, 예외 → 에러 응답 변환
```

- Repository 인터페이스는 단순 CRUD는 `JpaRepository`를 쓰고, 조건 조회는 `XxxQueryRepository`를 따로 두고 QueryDSL로 구현한다.
- 엔티티를 API 응답으로 직접 반환하지 않는다. 응답 DTO를 둔다.
  Ownership 미인증 Blog의 내부 필드나 `feedUrl`이 실수로 노출되는 것을 막는다.
  (`docs/specs/README.md`, PRD P-002, BR-008)
- 응답 래퍼와 에러 코드 규약은 `010-api-response-contract.md`를 따른다.
- 의존 방향은 모듈 경계가 아니라 ArchUnit 테스트로 검증한다. (`011`)

### 트랜잭션

- 클래스 기본은 `@Transactional(readOnly = true)`, 변경 메서드에만 `@Transactional`을 붙인다.
- **외부 HTTP 호출을 트랜잭션 안에서 하지 않는다.**
  Blog 탐색과 Feed 수집은 외부 응답을 먼저 받고, 그다음 짧은 트랜잭션에서 저장한다.
  외부 응답 지연이 DB 커넥션을 잡고 있으면 풀이 마른다.

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| JPA만 사용 (JPQL 문자열) | 의존성 없음 | 동적 조건 조합에서 타입 안전성 없음 | Feed와 친구 목록 쿼리가 조건 조합형이다 |
| Exposed (JetBrains) | Kotlin 친화적 DSL | Spring Data 생태계와 통합 비용, 팀 경험 부족 | 학습 비용이 이득보다 크다 |
| jOOQ | SQL 완전 제어, 생성 코드 품질 좋음 | JPA와 병행 시 이중 모델, 상용 라이선스 고려 | JPA를 쓰기로 한 이상 중복이다 |
| Kotlin JDSL | Kotlin 전용, KSP 불필요 | 생태계와 자료가 QueryDSL보다 얕음 | 팀이 QueryDSL을 쓰기로 이미 정했다 |
| QueryDSL KAPT | 자료 많음 | KAPT는 유지보수 모드, 빌드 느림 | 새 프로젝트에서 KAPT를 시작할 이유가 없다 |

---

## 4. Trade-off

- QueryDSL OpenFeign fork는 원본보다 사용 사례가 적다. 국내 자료 대부분은 `com.querydsl` 5.x 기준이라 그대로 복사하면 동작하지 않는다.
- KSP 생성 코드는 빌드 산출물이다. 클린 빌드 후 IDE 인덱싱 전까지 Q 클래스가 안 보이는 순간이 있다.
- QueryDSL 7.6은 Boot 4.1.0 / Hibernate 7.4.5 / Kotlin 2.4.10 / KSP 2.3.11 조합으로 빌드돼 있고, 그 조합으로 프로젝트 빌드와 테스트가 통과하는 것을 확인했다. (`./gradlew build`, 22 tests)
  다만 실제 쿼리 실행 검증은 각 기능 구현 시점에 쌓인다. Q 클래스 생성이나 런타임 쿼리 오류가 나면 이 Decision을 갱신한다.
- Kotlin 2.4.x는 Boot 4가 요구하는 2.2 이상 조건을 만족한다. QueryDSL 쪽 정렬을 따라간 결과다.

---

## 5. Consequences

### ID 전략과 맞물리는 부분

ULID를 애플리케이션에서 생성하므로 엔티티는 저장 전에도 ID를 가진다. Spring Data의 `save()`가 이를 기존 엔티티로 오인해 `merge()`를 호출하고, INSERT마다 SELECT가 한 번 더 나간다.

`Persistable<String>` 구현이나 `EntityManager.persist()` 직접 호출로 반드시 대응한다. 자세한 내용은 `006-id-strategy.md` 5장에 있다.

### 검증에서 확인할 것

- N+1 확인: Feed 조회와 친구 목록 조회는 쿼리 수를 테스트에서 검증한다. 사람 눈으로 로그만 보고 넘기지 않는다.
- QueryDSL로 만든 커서 페이지네이션은 동일 `publishedAt`이 여러 건일 때 중복/누락이 없어야 한다.
  (`docs/specs/feed.md` Acceptance Criteria)
- 통합 테스트는 Testcontainers Postgres에서 돌린다. (`001-database-selection.md`)

---

## 6. 재검토 조건

- QueryDSL fork가 Hibernate/Boot 신규 버전을 따라오지 못할 때
- 복잡한 집계 쿼리 비중이 커져 jOOQ나 네이티브 쿼리가 나을 때
- KSP codegen의 제약이 실제 매핑을 막을 때
