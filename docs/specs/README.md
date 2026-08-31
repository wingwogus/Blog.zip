# Feature Spec

기능 수준 Source of Truth를 관리하는 디렉토리다.

각 Spec은 다음 내용을 정의한다.

```text
각 기능이 어떻게 동작해야 하는가
어떤 비즈니스 규칙을 따라야 하는가
어떤 예외 상황을 처리해야 하는가
어떤 조건을 만족하면 완료된 것인가
```

## 파일 규칙

- 파일명은 기능 단위 kebab-case를 사용한다. (예: `blog-subscription.md`)
- 새 Spec은 `TEMPLATE.md`를 복사해 작성한다.
- 하나의 파일은 하나의 기능 도메인을 담당한다.

## MVP Spec 계획

`docs/PRD.md` 11장 MVP 범위를 기준으로 다음 Spec을 작성한다.

| Spec 파일 | 범위 | 상태 |
| --- | --- | --- |
| `auth.md` | 회원가입, 로그인, 로그아웃 | Draft |
| `blog-subscription.md` | 블로그 URL 입력, Blog 탐색, Subscription 생성/삭제 | Draft |
| `feed.md` | Post 수집, 피드 조회, 읽음 상태, 원문 이동 | Draft |
| `blog-ownership.md` | 내 블로그 등록, 소유권 인증, Ownership 해제 | Draft |
| `subscription-management.md` | 친구 목록 조회, 이름 수정, 구독 해제 | Draft |

Spec 작성 순서와 실제 파일 구성은 작업 시점에 조정할 수 있다.

---

## 공통 API 규약

모든 Spec의 API 요구사항은 아래 규약을 전제로 한다. 개별 Spec에서는 차이가 있는 부분만 다시 적는다.

### Base Path

```text
/api/v1
```

### 인증

- 인증이 필요한 API는 `Authorization: Bearer <accessToken>` 헤더를 요구한다.
- 토큰이 없거나 유효하지 않으면 `401 AUTH_001`.
- 다른 사용자의 리소스에 접근하면 `404`를 반환한다. 리소스 존재 여부를 노출하지 않는다.

### 시간

- 모든 시간 값은 ISO-8601 UTC 문자열로 주고받는다. (예: `2026-08-30T09:12:00Z`)
- 표시 시간대 변환은 클라이언트 책임이다.

### 목록 조회

- 커서 기반 페이지네이션을 사용한다.
- 요청: `?cursor=<opaque>&size=<int>` (`size` 기본 20, 최대 50)
- 응답:

```json
{
  "items": [],
  "nextCursor": "opaque-string-or-null"
}
```

- `nextCursor`가 `null`이면 마지막 페이지다.
- 커서 값은 클라이언트가 해석하지 않는다.

### 응답 래퍼

모든 응답은 공통 래퍼로 감싼다. 상세는 `docs/decisions/010-api-response-contract.md`에 있다.

성공:

```json
{ "success": true, "data": {} }
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

**각 Spec 8장의 Response 예시는 `data`에 들어가는 내용만 보여준 것이다.** 목록 조회의 `{items, nextCursor}`도 `data` 안에 들어간다. `204 No Content`는 본문이 없으며 래퍼도 없다.

- `code`는 기계가 판단하는 값이다. **클라이언트 분기는 `code`로만 한다.**
- `messageKey`는 안정적인 메시지 키다.
- `message`는 사람이 읽는 한국어 문장이다. 이 문자열로 분기하지 않으며 테스트로 고정하지도 않는다.
- 사용자 화면에 노출되는 `message`와 `detail`에 RSS, Atom, Feed 같은 기술 용어를 쓰지 않는다. (PRD P-002)

### 에러 코드 형식

```text
DOMAIN_NNN
```

도메인별 3자리 증가 번호이며 번호를 재사용하지 않는다. 전체 레지스트리는 `docs/decisions/010-api-response-contract.md` 2장에 있다. 새 에러 상황은 그 문서와 같은 PR에서 추가한다.

### 공통 에러 코드

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `COMMON_001` | 필수 값 누락, 형식 오류 |
| 400 | `COMMON_002` | JSON 파싱 실패 |
| 400 | `COMMON_003` | 커서 값 해석 실패 |
| 401 | `AUTH_001` | 인증 없음 또는 토큰 무효 |
| 403 | `AUTH_002` | 권한 없음 |
| 404 | `RESOURCE_001` | 경로 없음 |
| 429 | `COMMON_004` | 요청 제한 초과 |
| 500 | `COMMON_999` | 서버 내부 오류 |

### 용어 사용 규칙

- API 필드명과 내부 도메인에서는 `Blog`, `Subscription`, `Ownership`, `Post`를 그대로 쓴다.
- 사용자에게 보이는 문구에서는 "친구", "블로그", "새 글"을 쓴다.
- `friendName`은 **구독자 본인에게만 적용되는 Label**이다. 전역 표시명으로 쓰지 않는다. (PRD BR-004, BR-005)

---

## 참고

- 제품 수준 요구사항: `docs/PRD.md`
- 협업 규칙: `docs/GROUND_RULES.md`
- 기술 의사결정: `docs/decisions/`
