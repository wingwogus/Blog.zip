# Auth Spec

## 0. 문서 정보

- Feature: 계정 (회원가입 / 로그인 / 로그아웃)
- Status: Draft
- Last Updated: 2026-08-30
- 관련 PRD: `docs/PRD.md` 11장 MVP 범위 - 계정, P-005
- 관련 Decision: `docs/decisions/002-auth-strategy.md`, `docs/decisions/009-ephemeral-state.md`, `docs/decisions/010-api-response-contract.md`

---

## 1. 개요

Blog.zip을 사용하려면 사용자 계정이 필요하다. Subscription과 Ownership은 모두 특정 User에 귀속되기 때문이다.

계정 기능의 목표는 사용자가 최소한의 입력으로 가입하고 바로 블로그를 추가할 수 있게 하는 것이다. (P-005)

가입 직후 친구 초대, 프로필 작성, 블로그 연결 같은 추가 단계를 강제하지 않는다.

---

## 2. 범위

### In Scope

- 이메일 + 비밀번호 회원가입
- 로그인 및 토큰 발급
- 로그아웃
- 내 계정 정보 조회
- 로그인 실패 요청 제한

### Out of Scope

- 소셜 로그인 (Google, GitHub 등)
- 이메일 인증 메일 발송
- 비밀번호 재설정
- 회원 탈퇴
- 프로필 이미지

위 항목은 MVP 이후 별도 Spec에서 다룬다.

---

## 3. 용어

| 용어 | 정의 |
| --- | --- |
| User | Blog.zip에 가입한 사용자 |
| nickname | 사용자가 서비스 내에서 쓰는 표시 이름. 다른 사용자가 붙인 `friendName`과 무관하다. |
| accessToken | 인증이 필요한 API 호출에 사용하는 단기 토큰 |
| refreshToken | accessToken 재발급에 사용하는 장기 토큰 |

---

## 4. 사용자 흐름

```text
가입 화면
↓
이메일 / 비밀번호 / 이름 입력
↓
계정 생성
↓
자동 로그인
↓
Home (친구 블로그 추가 가능 상태)
```

가입 성공 시 별도의 로그인 절차를 다시 요구하지 않는다.

---

## 5. 기능 요구사항

### FR-001. 회원가입

사용자는 이메일, 비밀번호, 이름을 입력해 계정을 만든다.

입력 규칙:

| 항목 | 규칙 |
| --- | --- |
| email | 이메일 형식, 최대 254자, 대소문자 구분 없이 저장 및 비교 |
| password | 8자 이상 64자 이하 |
| nickname | 1자 이상 20자 이하, 앞뒤 공백 제거 후 빈 문자열 불가 |

- 이미 등록된 이메일로는 가입할 수 없다.
- 비밀번호는 복원 가능한 형태로 저장하지 않는다.
- 가입 성공 시 accessToken과 refreshToken을 함께 발급한다.

### FR-002. 로그인

사용자는 이메일과 비밀번호로 로그인한다.

- 인증 성공 시 accessToken과 refreshToken을 발급한다.
- 인증 실패 시 이메일 존재 여부를 구분할 수 있는 응답을 주지 않는다.

### FR-003. 토큰 재발급

accessToken이 만료된 경우 refreshToken으로 재발급한다.

- 무효하거나 만료된 refreshToken은 재발급하지 않는다.
- 재발급 성공 시 새 refreshToken을 발급하고 기존 것을 무효화한다. (회전)
- 이미 무효화된 refreshToken이 다시 사용되면 해당 사용자의 모든 refreshToken을 무효화한다. 응답은 동일하게 `401 AUTH_005`다.
- **클라이언트는 `401 AUTH_001`을 받으면 자동으로 재발급하고 원래 요청을 1회 재시도한다.** 사용자가 만료를 인지하지 않는다.
  재시도는 1회만 하며, 동시 요청은 재발급을 한 번만 호출한다. (`docs/decisions/002-auth-strategy.md`)

### FR-004. 로그아웃

사용자가 로그아웃하면 해당 refreshToken을 무효화한다.

- 이미 무효한 토큰으로 로그아웃을 요청해도 성공으로 응답한다. (멱등)

### FR-005. 내 계정 조회

로그인한 사용자는 자신의 이메일, 이름, 가입 시각을 조회할 수 있다.

### FR-006. 로그인 시도 제한

동일 이메일에 대해 연속 5회 로그인에 실패하면 10분 동안 로그인 요청을 거부한다.

- 카운터 기준은 정규화된 이메일이며 윈도우는 10분이다. 로그인 성공 시 초기화된다.
- 차단 상태에서도 이메일 존재 여부를 노출하지 않는다. 등록되지 않은 이메일도 같은 방식으로 카운트한다.
- 값의 근거는 `docs/decisions/002-auth-strategy.md`에 있다.

### FR-007. API 요청 제한

사용자별, IP별 요청 제한을 적용한다.

| 대상 | 제한 |
| --- | --- |
| 인증된 사용자 전체 요청 | 분당 120회 |
| 미인증 요청 (IP 기준) | 분당 30회 |
| 로그인 / 회원가입 (IP 기준) | 분당 10회 |

- 초과 시 `429 COMMON_004`이며 `Retry-After` 헤더를 포함한다.
- 기능별 제한은 각 Spec에서 정의한다. (Blog 탐색, Ownership 확인)

---

## 6. 비즈니스 규칙

| ID | 규칙 | 근거 |
| --- | --- | --- |
| AUTH-BR-001 | 하나의 이메일로 하나의 계정만 만들 수 있다. | 계정 식별 |
| AUTH-BR-002 | 가입 직후 추가 온보딩 없이 블로그 구독이 가능해야 한다. | PRD P-005 |
| AUTH-BR-003 | nickname은 서비스 내 표시 이름이며 고유하지 않다. | PRD P-007 |
| AUTH-BR-004 | nickname은 다른 사용자가 지정한 `friendName`을 덮어쓰지 않는다. | PRD BR-004, BR-005 |
| AUTH-BR-005 | 계정 존재만으로 어떤 Blog의 소유도 주장하지 않는다. | PRD BR-001, BR-008 |

---

## 7. 데이터

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| id | User 식별자 | O |
| email | 로그인 식별자. 정규화(소문자)해 저장 | O |
| passwordHash | 단방향 해시된 비밀번호 | O |
| nickname | 서비스 내 표시 이름 | O |
| createdAt | 가입 시각 | O |

비밀번호는 BCrypt로 해시한다.

refreshToken은 별도 테이블에 저장하며 원문이 아닌 SHA-256 해시를 저장한다.

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| userId | 소유자 | O |
| tokenHash | refreshToken의 SHA-256 해시 (UNIQUE) | O |
| expiresAt | 만료 시각 | O |
| revokedAt | 무효화 시각 | X |

만료 시간과 저장 위치는 `docs/decisions/002-auth-strategy.md`에 있다. accessToken 30분, refreshToken 14일이며 refreshToken은 `HttpOnly` 쿠키로 전달한다.

---

## 8. API 요구사항

공통 규약은 `docs/specs/README.md`를 따른다.

### POST /api/v1/auth/signup

- 목적: 계정 생성 및 자동 로그인
- 인증 필요: X

Request:

```json
{
  "email": "user@example.com",
  "password": "password1234",
  "nickname": "재현"
}
```

Response (201):

```json
{
  "user": {
    "id": "usr_01H...",
    "email": "user@example.com",
    "nickname": "재현",
    "createdAt": "2026-08-30T09:12:00Z"
  },
  "accessToken": "..."
}
```

refreshToken은 `Set-Cookie`로 전달한다.

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `COMMON_001` | 형식 또는 길이 규칙 위반 |
| 409 | `AUTH_003` | 이미 등록된 이메일 |

### POST /api/v1/auth/login

- 목적: 토큰 발급
- 인증 필요: X

Request:

```json
{
  "email": "user@example.com",
  "password": "password1234"
}
```

Response (200): `signup`과 동일한 형태

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `COMMON_001` | 필수 값 누락 |
| 401 | `AUTH_004` | 이메일 또는 비밀번호 불일치 |
| 429 | `AUTH_006` | 로그인 시도 제한 초과 |

### POST /api/v1/auth/token/refresh

- 목적: accessToken 재발급
- 인증 필요: X (쿠키의 refreshToken으로 검증)

Request: 본문 없음. refreshToken은 `HttpOnly` 쿠키로 전달된다.

Response (200):

```json
{
  "accessToken": "..."
}
```

새 refreshToken은 `Set-Cookie`로 전달한다. 응답 본문에 넣지 않는다.

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 401 | `AUTH_005` | 무효, 만료, 이미 무효화된 토큰 |

### POST /api/v1/auth/logout

- 목적: refreshToken 무효화
- 인증 필요: O

Request: 본문 없음. 쿠키의 refreshToken을 무효화하고 쿠키를 만료시킨다.

Response (204): 본문 없음

### GET /api/v1/users/me

- 목적: 내 계정 정보 조회
- 인증 필요: O

Response (200):

```json
{
  "id": "usr_01H...",
  "email": "user@example.com",
  "nickname": "재현",
  "createdAt": "2026-08-30T09:12:00Z"
}
```

---

## 9. 예외 및 실패 처리

| 상황 | 기대 동작 |
| --- | --- |
| 이메일 대소문자만 다른 중복 가입 | `409 AUTH_003` |
| 비밀번호 길이 미달 | `400 COMMON_001`, 어떤 규칙을 위반했는지 안내 |
| 존재하지 않는 이메일로 로그인 | `401 AUTH_004` (존재 여부 노출 금지) |
| 로그인 시도 제한 초과 | `429 AUTH_006` |
| 만료된 accessToken으로 API 호출 | `401 AUTH_001`, 클라이언트는 재발급 후 1회 재시도 |
| 재발급도 실패 | 로그인 화면으로 이동 |
| 무효 토큰으로 로그아웃 | `204` (멱등) |

에러 로그에 비밀번호, 토큰 원문을 남기지 않는다.

---

## 10. Acceptance Criteria

- [ ] 유효한 입력으로 가입하면 `201`과 함께 토큰이 발급되고, 추가 로그인 없이 인증이 필요한 API를 호출할 수 있다.
- [ ] 동일 이메일(대소문자 차이 포함)로 재가입하면 `409 AUTH_003`를 받는다.
- [ ] 비밀번호가 8자 미만이면 `400 COMMON_001`를 받고 계정이 생성되지 않는다.
- [ ] 존재하지 않는 이메일과 잘못된 비밀번호의 로그인 실패 응답이 code와 HTTP status 모두 동일하다.
- [ ] 저장된 비밀번호는 평문이 아니며 원문 복원이 불가능하다.
- [ ] 로그아웃 후 해당 refreshToken으로 재발급을 요청하면 `401 AUTH_005`을 받는다.
- [ ] 이미 무효한 refreshToken으로 로그아웃해도 `204`를 받는다.
- [ ] 연속 5회 로그인 실패 후 6번째 요청에서 `429 AUTH_006`을 받는다.
- [ ] 재발급에 성공하면 이전 refreshToken으로는 재발급할 수 없다.
- [ ] 무효화된 refreshToken을 재사용하면 해당 사용자의 다른 refreshToken도 무효가 된다.
- [ ] 같은 refreshToken으로 동시에 재발급을 요청하면 하나만 성공하고, 재사용 탐지 뒤에는 경쟁 요청으로 발급된 refreshToken도 사용할 수 없다.
- [ ] 요청 제한 초과 응답에 `Retry-After` 헤더가 있다.
- [ ] 토큰 없이 `GET /api/v1/users/me`를 호출하면 `401 AUTH_001`를 받는다.
- [ ] 가입 직후 구독한 Blog가 0개인 상태에서도 Home과 친구 블로그 추가 화면이 정상 동작한다.

---

## 11. Open Questions

- 이메일 인증을 언제 도입할지
- 회원 탈퇴 시 기존 Subscription과 Ownership 처리 방식
- 로그아웃 후 accessToken이 최대 30분간 유효한 문제를 언제 다룰지 (`docs/decisions/002-auth-strategy.md` 4장)
