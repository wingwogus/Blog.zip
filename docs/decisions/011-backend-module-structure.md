# 011. 백엔드 모듈 구조 - 단일 모듈로 시작

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: -
- 관련 Decision: `007-persistence-stack.md`, `010-api-response-contract.md`

---

## 1. Context

기존 팀 템플릿(`springboot-kotlin-initial-template`)은 Gradle 멀티 모듈이다.

```text
api -> application -> domain
batch -> application -> domain
```

이 구조를 Blog.zip에 그대로 가져올지 판단해야 한다.

멀티 모듈이 주는 것은 **컴파일러가 강제하는 의존 방향**이다. `domain`이 `api`를 참조하면 빌드가 실패한다. 패키지 규칙만으로는 리뷰가 놓치면 통과한다.

멀티 모듈이 요구하는 것은 4개의 `build.gradle.kts` 관리, 의존성 배치 판단, 모듈 경계를 넘는 리팩터링 비용이다.

Blog.zip의 규모: 엔티티 6개(User, Blog, Subscription, Ownership, OwnershipVerification, Post + 상태 테이블 2개), 엔드포인트 15개 내외, 배포 단위 1개.

---

## 2. Decision

**단일 Gradle 모듈로 시작한다. 패키지로 계층을 구분한다.**

```text
backend/
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
└── src/
    ├── main/
    │   ├── kotlin/com/blogzip/
    │   │   ├── BlogzipApplication.kt
    │   │   ├── common/
    │   │   │   ├── ApiResponse.kt
    │   │   │   ├── ErrorCode.kt
    │   │   │   ├── BusinessException.kt
    │   │   │   ├── GlobalExceptionHandler.kt
    │   │   │   └── ulid/
    │   │   ├── config/
    │   │   │   └── SwaggerConfig.kt
    │   │   ├── auth/
    │   │   │   ├── config/
    │   │   │   ├── controller/
    │   │   │   ├── service/
    │   │   │   ├── repository/
    │   │   │   └── domain/
    │   │   ├── blog/
    │   │   ├── subscription/
    │   │   │   └── config/
    │   │   ├── feed/
    │   │   └── ownership/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── messages.properties
    │       └── db/migration/
    └── test/kotlin/com/blogzip/
```

- Base package: `com.blogzip`
- **기능(feature) 우선, 그다음 역할.** `subscription/controller`, `subscription/service`,
  `subscription/repository`, `subscription/domain` 형태다. 템플릿의 `api.auth.controller`가
  계층 우선인 것과 반대다.
  기능 하나를 작업할 때 한 디렉토리 안에서 끝나는 쪽을 택했다.
- `common`에는 기능에 속하지 않는 횡단 관심사만 둔다. 설정도 가능한 한 해당 기능의
  `config`에 둔다. 루트 `config`에는 애플리케이션 공통 설정만 둔다.

### 의존 방향

```text
controller -> service -> domain
service -> repository -> domain
```

컴파일러가 막아주지 않으므로 리뷰에서 확인한다. 확인할 항목:

- `domain` 패키지가 Spring Web, `controller`, `service`, `repository`를 import하지 않는다.
- `service`가 `controller`의 DTO를 import하지 않는다.
- 엔티티가 API 응답으로 직접 나가지 않는다.

이건 ArchUnit으로 테스트할 수 있다. 모듈 분리 대신 이걸 넣는 게 이 결정의 전제다.
**의존 방향 검증 테스트를 첫 구현 PR에 포함한다.**

### batch 모듈을 만들지 않는다

Feed 수집은 Spring Batch가 필요한 작업이 아니다. `@Scheduled` 하나로 충분하다. (`004-post-collection.md`)

수집 로직은 `feed/service`에 두고 스케줄러 진입점만 얇게 만든다. 인스턴스가 1대이므로 API 서버와 같은 프로세스에서 돈다. (`009-ephemeral-state.md`)

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| 템플릿 그대로 4모듈 | 의존 방향을 컴파일러가 강제, 팀에 익숙 | 이 규모에서 빌드 설정 관리 비용이 이득보다 크다. 초기에 모듈 경계를 자주 옮기게 된다 | 규모가 안 맞는다. ArchUnit으로 같은 목적을 달성할 수 있다 |
| 2모듈 (`app` + `domain`) | 핵심 경계만 강제 | 경계 하나만 막는데 설정 부담은 멀티 모듈 전체 | 얻는 게 적다 |
| 계층 우선 패키지 (`domain/subscription`) | 계층 구조가 한눈에 보임 | 기능 하나 작업에 4개 디렉토리를 오간다 | 기능 응집도를 택했다 |

---

## 4. Trade-off

- 의존 방향이 컴파일 에러로 잡히지 않는다. ArchUnit 테스트가 그 역할을 대신하는데, 테스트를 안 돌리면 무의미하다. CI에서 반드시 실행한다.
- 나중에 모듈 분리가 필요해지면 패키지 이동 작업이 생긴다. 다만 패키지 구조가 이미 계층으로 나뉘어 있으면 분리는 기계적이다.
- 팀원이 기존 템플릿 구조에 익숙하다면 첫 진입에 약간의 혼란이 있다.

---

## 5. Consequences: 템플릿에서 가져올 때 고쳐야 하는 것

템플릿을 복사한 뒤 다음을 수정한다. 그냥 옮기면 안 되는 것들이다.

### 반드시 바꿀 것

| 항목 | 템플릿 | Blog.zip | 근거 |
| --- | --- | --- | --- |
| Kotlin | 1.9.25 | 2.4.10 | `007` |
| Spring Boot | 3.5.4 | 4.1.1 | `007` |
| `kotlin("plugin.jpa")` | 1.9.10 (버전 불일치) | Kotlin 버전과 일치 | 템플릿 버그다 |
| 로컬 DB | H2 in-memory | PostgreSQL 컨테이너 | `001` |
| `domain` 드라이버 | `mysql-connector-j` | `postgresql` | `001` |
| `ddl-auto` | `create` | `validate` + Flyway | `008` |
| ID | (템플릿은 Long) | ULID `char(26)` | `006` |
| 단기 상태 | Redis | Caffeine 인메모리 | `009` |
| refreshToken | Redis | PostgreSQL | `009` |
| `ApiError.message` | `messageKey` 값이 들어감 | `messageKey`와 `message` 분리 | `010` |
| 모듈 | 4개 | 1개 | 이 문서 |
| Base package | `com.example` | `com.blogzip` | |
| 패키지 배치 | 계층 우선 | 기능 우선 | 이 문서 |
| JWT 시크릿 | `application-local.yml`에 하드코딩 | 환경변수, 커밋 금지 | 아래 |

### 하드코딩된 시크릿

템플릿 `application-local.yml`에 Base64 JWT 시크릿이 그대로 들어 있다. 로컬 전용 dummy라고 주석에 적혀 있지만, 이 파일을 복사하면 그 값이 Blog.zip 저장소에도 커밋된다.

**복사하지 않는다.** 환경변수로 받고, 없으면 기동을 실패시킨다. 기본값을 넣지 않는다. 기본값이 있으면 운영에서 dummy 키로 도는 사고가 가능하다.

### 버릴 것

| 항목 | 이유 |
| --- | --- |
| `batch` 모듈 | Spring Batch가 필요 없다 |
| Kakao OIDC (`KakaoLoginService`, `KakaoOidcTokenVerifier`, nonce replay) | MVP는 이메일 로그인만. PRD 11장 |
| SMTP / `EmailSender` / 이메일 인증 코드 | `auth.md` Out of Scope |
| `spring-security-oauth2-jose` | Kakao OIDC 전용 |
| Redis 관련 전부 | `009` |
| `TestController` | 샘플 |
| `.omx/` 디렉토리 | 하네스 산출물. 팀 Source of Truth가 아니다 (루트 `AGENTS.md`) |
| `.DS_Store` | `.gitignore`에 넣는다 |

### 가져올 것

| 항목 | 비고 |
| --- | --- |
| `ApiResponse` / `ApiError` | `message` 분리만 적용 |
| `ErrorCode` enum 형태 | 값은 `010`의 레지스트리로 교체 |
| `BusinessException`, `ApplicationException` | 그대로 |
| `GlobalExceptionHandler` | 핸들러 구성 그대로. 커서 파싱 실패(`COMMON_003`) 핸들러 추가 |
| `MDCLoggingFilter` | 그대로. traceId/eventId 기반 로깅이 이미 잘 잡혀 있다 |
| `LoggingUtil` | 그대로 |
| `JwtAuthenticationFilter`, `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler` | 이메일 로그인만 남기고 정리 |
| `SecurityConfig` | OAuth 관련 제거 |
| `SwaggerConfig` | 프로젝트 정보만 교체 |
| `TokenProvider` | 그대로. refreshToken 저장소만 DB로 교체 |
| `application-{local,dev,prod}.yml` 분리 구조 | 값은 교체 |
| 로깅 패턴 (`traceId`, `eventId`, `clientIp`) | 그대로. `userId` MDC 키 유지 |

### 새로 넣을 것

- Flyway + `db/migration/V1__*.sql`
- Testcontainers Postgres 베이스 테스트 클래스
- QueryDSL KSP 설정 (`007`)
- Caffeine 캐시 설정
- `messages.properties`
- ArchUnit 의존 방향 테스트
- `docker-compose.yml` (Postgres)
- `.gitignore` (`.DS_Store`, `build/`, `.gradle/`, 로컬 환경 파일)

### 버전 조합 검증 결과

Boot 4.1.1 + Kotlin 2.4.10 + QueryDSL 7.6 + KSP 2.3.11 조합으로 `./gradlew build`가 통과한다. QueryDSL 7.6의 부모 POM이 같은 조합으로 선언돼 있어 정렬이 맞는다. (`007` 2장)

포팅 과정에서 Boot 3 → 4 차이로 실제로 막힌 지점은 `007` 2장 "Boot 4에서 걸린 것"에 정리했다. Jackson 3 전환과 Flyway starter 필요 여부가 핵심이다.

---

## 6. 재검토 조건

- 수집 워커를 별도 배포 단위로 분리할 때
- 팀 규모가 커져 모듈 경계로 작업을 나눠야 할 때
- ArchUnit 테스트로도 의존 방향 위반이 반복적으로 새어 들어올 때
