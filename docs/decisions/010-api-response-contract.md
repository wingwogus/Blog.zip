# 010. API 응답 및 에러 코드 규약

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/README.md` 공통 API 규약, 각 Spec 8장
- 관련 Decision: `007-persistence-stack.md`, `011-backend-module-structure.md`

---

## 1. Context

Spec 5개가 각각 API를 정의하고 있고, 에러 응답 포맷은 `docs/specs/README.md`에 `{code, message}`로만 적혀 있었다.

기존 팀 프로젝트(ChamChamCham)에 이미 검증된 형식이 있다.

- `ApiResponse<T>` 래퍼: `{success, data, error}`
- `ErrorCode` enum: `(code, messageKey, status)`, 코드는 `DOMAIN_NNN` 형식
- `BusinessException(ErrorCode)` → `GlobalExceptionHandler`가 응답으로 변환

같은 팀이 같은 형식을 쓰면 프론트 처리 코드와 리뷰 기준을 재사용할 수 있다.

---

## 2. Decision

**ChamChamCham의 응답 규약을 그대로 채택한다.** 한 가지만 바꾼다.

### 응답 래퍼

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
)

data class ApiError(
    val code: String,
    val messageKey: String,
    val message: String,
    val detail: Any? = null,
)
```

성공:

```json
{ "success": true, "data": { } }
```

실패:

```json
{
  "success": false,
  "error": {
    "code": "SUBSCRIPTION_002",
    "messageKey": "error.already_subscribed",
    "message": "이미 구독 중인 블로그입니다.",
    "detail": null
  }
}
```

- 각 Spec 8장의 Response 예시는 **`data`에 들어가는 내용**을 보여준 것이다. 실제 응답은 위 래퍼로 감싼다.
- 목록 조회의 `{items, nextCursor}`도 `data` 안에 들어간다.
- `204 No Content`는 본문이 없다. 래퍼도 없다.

### ChamChamCham과 다르게 하는 한 가지

ChamChamCham의 `ApiResponse.fail()`은 `messageKey`를 `ApiError.message` 자리에 넣는다. 결과적으로 클라이언트는 `message: "error.invalid_input"`을 받는다. 필드 이름이 `message`인데 값은 키다.

Blog.zip은 `messageKey`와 `message`를 분리한다.

- `messageKey`: `error.already_subscribed` 같은 안정적인 키. 기계가 쓴다.
- `message`: 사람이 읽는 한국어 문장. `GlobalExceptionHandler`가 `MessageSource`로 `messageKey`를 해석해 채운다.

이유: 프론트가 코드-문구 대응표를 따로 관리하지 않아도 화면에 바로 쓸 문장을 받는다. 서버가 문구의 단일 출처가 된다.

메시지는 `api/src/main/resources/messages.properties`에 둔다.

### 클라이언트 분기 규칙

- **분기는 `code`로만 한다.** `message`나 `messageKey` 문자열로 분기하지 않는다.
- `message`는 화면 표시와 로그용이다. 문구는 예고 없이 바뀔 수 있다.
- 테스트도 `code`와 HTTP status만 검증한다. 문구를 테스트로 고정하지 않는다.

### 에러 코드 형식

```text
DOMAIN_NNN
```

- `DOMAIN`: 대문자 도메인 이름
- `NNN`: 도메인 내 3자리 증가 번호. **재사용하지 않는다.** 코드가 없어져도 번호를 비워둔다.
- 공통 시스템 에러는 `COMMON_999`처럼 900번대를 쓴다.

### 에러 코드 레지스트리

각 Spec에서 정의한 에러를 아래 코드로 확정한다. Spec 8장의 `code` 컬럼은 이 값을 쓴다.

#### COMMON / RESOURCE

| code | 상수 | status | 상황 |
| --- | --- | --- | --- |
| `COMMON_001` | `INVALID_INPUT` | 400 | 형식, 길이, 필수 값 위반 |
| `COMMON_002` | `INVALID_JSON` | 400 | JSON 파싱 실패 |
| `COMMON_003` | `INVALID_CURSOR` | 400 | 커서 값 해석 실패 |
| `COMMON_004` | `TOO_MANY_REQUESTS` | 429 | 공통 요청 제한 |
| `COMMON_999` | `INTERNAL_ERROR` | 500 | 서버 내부 오류 |
| `RESOURCE_001` | `RESOURCE_NOT_FOUND` | 404 | 경로 없음 |

#### AUTH / USER (`docs/specs/auth.md`)

| code | 상수 | status | Spec 표기 |
| --- | --- | --- | --- |
| `AUTH_001` | `UNAUTHORIZED` | 401 | `UNAUTHORIZED` |
| `AUTH_002` | `FORBIDDEN` | 403 | - |
| `AUTH_003` | `DUPLICATE_EMAIL` | 409 | `EMAIL_ALREADY_EXISTS` |
| `AUTH_004` | `INVALID_CREDENTIALS` | 401 | `INVALID_CREDENTIALS` |
| `AUTH_005` | `INVALID_REFRESH_TOKEN` | 401 | `INVALID_REFRESH_TOKEN` |
| `AUTH_006` | `TOO_MANY_LOGIN_ATTEMPTS` | 429 | `TOO_MANY_LOGIN_ATTEMPTS` |
| `AUTH_007` | `MALFORMED_JWT` | 400 | - |
| `USER_001` | `USER_NOT_FOUND` | 404 | - |

#### BLOG (`docs/specs/blog-subscription.md`, `003-blog-discovery.md`)

| code | 상수 | status | Spec 표기 |
| --- | --- | --- | --- |
| `BLOG_001` | `INVALID_BLOG_URL` | 400 | `INVALID_URL` |
| `BLOG_002` | `BLOCKED_BLOG_URL` | 400 | `BLOCKED_URL` |
| `BLOG_003` | `BLOG_NOT_REACHABLE` | 404 | `BLOG_NOT_REACHABLE` |
| `BLOG_004` | `BLOG_NOT_SUPPORTED` | 422 | `BLOG_NOT_SUPPORTED` |
| `BLOG_005` | `BLOG_LOOKUP_EXPIRED` | 400 | `LOOKUP_EXPIRED` |
| `BLOG_006` | `TOO_MANY_LOOKUP_REQUESTS` | 429 | `TOO_MANY_REQUESTS` |

#### SUBSCRIPTION (`blog-subscription.md`, `subscription-management.md`)

| code | 상수 | status | Spec 표기 |
| --- | --- | --- | --- |
| `SUBSCRIPTION_001` | `SUBSCRIPTION_NOT_FOUND` | 404 | `NOT_FOUND` |
| `SUBSCRIPTION_002` | `ALREADY_SUBSCRIBED` | 409 | `ALREADY_SUBSCRIBED` |

#### POST (`docs/specs/feed.md`)

| code | 상수 | status | Spec 표기 |
| --- | --- | --- | --- |
| `POST_001` | `POST_NOT_FOUND` | 404 | `NOT_FOUND` |

#### OWNERSHIP (`docs/specs/blog-ownership.md`)

| code | 상수 | status | Spec 표기 |
| --- | --- | --- | --- |
| `OWNERSHIP_001` | `OWNERSHIP_NOT_FOUND` | 404 | `NOT_FOUND` (연결된 Blog 없음) |
| `OWNERSHIP_002` | `BLOG_ALREADY_OWNED` | 409 | `BLOG_ALREADY_OWNED` |
| `OWNERSHIP_003` | `USER_ALREADY_OWNS_BLOG` | 409 | `USER_ALREADY_OWNS_BLOG` |
| `OWNERSHIP_004` | `VERIFICATION_NOT_FOUND` | 404 | `NOT_FOUND` (verification) |
| `OWNERSHIP_005` | `VERIFICATION_EXPIRED` | 409 | `VERIFICATION_EXPIRED` |
| `OWNERSHIP_006` | `VERIFICATION_CODE_NOT_FOUND` | 422 | `CODE_NOT_FOUND` |
| `OWNERSHIP_007` | `TOO_MANY_VERIFICATION_ATTEMPTS` | 429 | `TOO_MANY_REQUESTS` |

### 다른 사용자의 리소스 접근

`SUBSCRIPTION_001`, `OWNERSHIP_004`처럼 "없음"과 "권한 없음"을 같은 코드로 응답한다. 리소스 존재 여부를 노출하지 않는다. 각 Spec의 Acceptance Criteria에 이미 검증 항목이 있다.

### 사용자 노출 문구 규칙

`message`와 `detail`에 RSS, Atom, Feed 같은 기술 용어를 쓰지 않는다. (PRD P-002)

`messages.properties`에 그런 단어가 들어가지 않는지 테스트로 검증한다. 이건 문구가 아니라 제품 원칙 위반 여부를 보는 것이므로 테스트 대상이다.

### API 문서화

SpringDoc OpenAPI로 Swagger UI를 제공한다.

- 의존성: `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- 설정 위치: `api/.../config/SwaggerConfig.kt` (템플릿과 동일한 위치)
- 각 엔드포인트에 발생 가능한 에러 코드를 문서에 남긴다. 프론트가 분기할 코드를 Swagger에서 확인할 수 있어야 한다.
- 운영 프로필에서 Swagger UI를 노출할지는 배포 시점에 정한다. 기본은 `local`, `dev`만 노출한다.

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| 래퍼 없이 payload 직접 반환 + 에러만 별도 포맷 | 응답이 간결, HTTP status가 유일한 성공 신호 | 팀의 기존 프론트 처리 코드와 다르다 | 팀 일관성이 더 가치 있다 |
| RFC 9457 Problem Details | 표준 | `success`/`data` 래퍼와 섞이면 어색하고 팀에 익숙하지 않다 | 기존 형식과 충돌 |
| ChamChamCham 그대로 (message에 key) | 완전 동일 | 클라이언트가 키를 받아 문구 표를 따로 관리해야 한다 | 문구 출처가 둘로 갈라진다 |
| 문자열 에러 코드 (`ALREADY_SUBSCRIBED`) | 코드만 보고 의미 파악 | 도메인 구분과 번호 관리가 없어 중복/충돌이 생긴다 | 팀 형식이 `DOMAIN_NNN`이다 |
| REST Docs | 테스트 기반이므로 문서가 항상 정확 | 작성 비용이 크다 | MVP에서는 SpringDoc이 빠르다 |

---

## 4. Trade-off

- 래퍼 때문에 응답이 한 겹 깊어진다. 프론트는 `res.data.data`를 다루게 된다.
- `messageKey`와 `message`를 둘 다 보내므로 응답이 약간 커진다. 대신 프론트가 문구 표를 관리하지 않는다.
- `messages.properties`가 사실상 사용자 노출 문구의 단일 출처가 된다. 문구 변경도 서버 배포가 필요하다.
- 에러 코드 번호를 재사용하지 않으므로 시간이 지나면 번호에 구멍이 생긴다. 의도한 것이다.

---

## 5. Consequences

### Spec 수정 필요

각 Spec 8장 Error 표의 `code` 컬럼을 위 레지스트리 값으로 바꾼다. `docs/specs/README.md`에 래퍼 구조를 추가한다. 같은 PR에서 처리한다.

### 구현에서 지킬 것

- 새 에러 상황은 `ErrorCode`에 추가한다. 응답 본문을 컨트롤러에서 직접 만들지 않는다.
- 예상된 실패는 `BusinessException(ErrorCode.X)`로 던진다.
- `GlobalExceptionHandler`가 변환을 담당한다. 컨트롤러에서 try-catch로 에러 응답을 만들지 않는다.
- 예외 메시지 원문을 클라이언트에 그대로 내려보내지 않는다.
- 예상치 못한 예외는 `eventId`를 발급해 `detail`에 넣고, 서버 로그와 대조할 수 있게 한다. (ChamChamCham과 동일)

### 로그

- 비즈니스 예외는 WARN, 예상치 못한 예외는 ERROR.
- 비밀번호, 토큰, 인증 코드를 로그에 남기지 않는다.

---

## 6. 재검토 조건

- 다국어 지원이 필요해질 때 (`Accept-Language` 기반 `MessageSource` 확장)
- 프론트에서 래퍼가 실질적으로 불편할 때
- 에러 코드 수가 늘어 도메인 구분이 부족해질 때
