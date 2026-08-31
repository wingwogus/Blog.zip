# Blog Ownership Spec

## 0. 문서 정보

- Feature: 내 블로그 연결 (Ownership 인증)
- Status: Draft
- Last Updated: 2026-08-30
- 관련 PRD: `docs/PRD.md` 9장 Scenario 3-4, 10장 내 블로그 연결, 11장 MVP 범위 - 내 블로그
- 관련 Decision: `docs/decisions/005-ownership-verification.md` (미작성), `docs/decisions/003-blog-discovery.md`, `docs/decisions/010-api-response-contract.md`

---

## 1. 개요

사용자가 자신이 운영하는 블로그를 계정에 연결하는 기능이다.

구독과 달리 소유권 주장에는 인증이 필요하다. (P-003, BR-003)

인증 전에는 서비스가 그 사용자를 해당 Blog의 공식 운영자로 표시하거나 보장하지 않는다. (BR-008)

MVP는 Blog 하나에 대한 Ownership만 다룬다. 다수 Blog Ownership은 제외 범위다.

---

## 2. 범위

### In Scope

- 내 블로그 URL 등록 및 인증 요청 시작
- 소유권 인증 코드 발급
- 인증 확인 (검증 실행)
- Ownership 생성
- 연결된 내 Blog 조회
- Ownership 해제

### Out of Scope

- 한 User가 여러 Blog를 소유하는 경우 (PRD 12장 제외 범위)
- 플랫폼 OAuth 기반 소유권 확인
- 도메인 DNS 레코드 인증
- Ownership 기반 Challenge 집계 (Phase 3)

---

## 3. 용어

| 용어 | 정의 |
| --- | --- |
| Ownership | 특정 User가 특정 Blog의 실제 운영자임이 인증된 관계 |
| verification | 소유권 확인 시도 1건 |
| verificationCode | Blog.zip이 발급하는 소유 증명 문자열 |
| PENDING | 코드 발급 후 확인 대기 상태 |
| VERIFIED | 확인 성공, Ownership 생성됨 |

---

## 4. 사용자 흐름

```text
Profile
↓
내 블로그 연결
↓
블로그 URL 입력
↓
Blog 탐색
↓
인증 코드 발급
↓
사용자가 블로그에 코드 게시
↓
확인 요청
↓
Blog.zip이 코드 확인
↓
Ownership 생성
↓
Profile에 내 블로그 표시
```

---

## 5. 기능 요구사항

### FR-001. 내 블로그 등록 시작

사용자가 자신의 블로그 URL을 입력하면 서버는 Blog를 탐색한다.

- URL 검증과 Blog 탐색 규칙은 `docs/specs/blog-subscription.md` FR-001, FR-002와 동일하다.
- 이미 존재하는 Blog면 재사용한다. 새 Blog를 만들지 않는다.
- 탐색 실패 시 인증을 시작하지 않는다.

### FR-002. 인증 코드 발급

탐색에 성공하면 해당 Blog에 대한 verification을 `PENDING`으로 만들고 코드를 발급한다.

- 코드는 추측하기 어려운 값이며 사용자별, Blog별로 다르다.
- 코드에는 만료 시간이 있다.
- 같은 사용자가 같은 Blog에 대해 재요청하면 기존 유효한 코드를 반환한다. 새로 만들지 않는다.
- 만료된 경우 새 코드를 발급한다.

### FR-003. 소유 증명 방법

사용자는 다음 중 한 가지 방법으로 코드를 노출한다.

| 방법 | 내용 |
| --- | --- |
| 새 글 게시 | 블로그에 코드 문자열을 포함한 글을 올린다 |
| 블로그 소개란 | 블로그 소개/설명에 코드 문자열을 넣는다 |

MVP에서 지원할 방법의 최종 목록과 플랫폼별 확인 위치는 `docs/decisions/005-ownership-verification.md`에서 정한다.

사용자에게 보여주는 안내에는 RSS, Atom 같은 용어를 쓰지 않는다. (P-002)

### FR-004. 인증 확인

사용자가 확인을 요청하면 서버는 Blog에서 코드를 찾는다.

- 확인 대상: 최근 글 목록과 블로그 설명.
- 코드를 찾으면 verification을 `VERIFIED`로 바꾸고 Ownership을 생성한다.
- 찾지 못하면 실패로 응답하고 verification은 `PENDING`으로 유지한다. 사용자는 다시 시도할 수 있다.
- 확인 요청에는 사용자별 요청 제한을 둔다.
- 확인은 사용자의 명시적 요청으로만 실행한다. 자동 재확인은 하지 않는다.

### FR-005. Ownership 생성 효과

Ownership이 생성되면 다음이 성립한다.

- Blog에 `ownerUserId`가 설정된다.
- 사용자 Profile에 연결된 내 블로그가 표시된다.
- 기존 구독자의 Subscription은 그대로 유지된다. (BR-007)
- 기존 구독자가 지정한 friendName을 변경하지 않는다. (BR-004, BR-007)
- Ownership은 구독 관계를 만들지 않는다. 자신의 블로그를 피드에서 보려면 별도로 구독해야 한다.

### FR-006. 중복 Ownership 처리

- 이미 다른 User가 Ownership을 가진 Blog에는 인증을 시작할 수 없다.
- 한 User는 하나의 Blog만 소유할 수 있다. 이미 소유한 Blog가 있으면 먼저 해제해야 한다.
- Ownership이 없는 상태에서는 여러 사용자가 동시에 `PENDING` verification을 가질 수 있다. 먼저 확인에 성공한 사용자가 Ownership을 얻는다.

### FR-007. 연결된 내 Blog 조회

사용자는 자신의 Ownership 상태를 조회할 수 있다.

- 없으면 연결되지 않은 상태를 반환한다.
- `PENDING` verification이 있으면 코드와 안내를 함께 반환한다.

### FR-008. Ownership 해제

사용자는 Ownership을 해제할 수 있다.

- Blog의 `ownerUserId`가 비워진다.
- Blog와 Post는 삭제하지 않는다.
- 다른 사용자의 Subscription은 영향을 받지 않는다.
- 해제 후 같은 Blog에 다시 인증을 시작할 수 있다. 이전 코드는 재사용하지 않는다.

---

## 6. 비즈니스 규칙

| ID | 규칙 | 근거 |
| --- | --- | --- |
| OWN-BR-001 | Blog를 자신의 것으로 연결하려면 인증이 필요하다. | PRD BR-003 |
| OWN-BR-002 | 인증 전에는 공식 운영자임을 UI/API에서 보장하지 않는다. | PRD BR-008 |
| OWN-BR-003 | Ownership 생성 후에도 기존 Subscription은 유지된다. | PRD BR-007 |
| OWN-BR-004 | 기존 구독자가 지정한 friendName을 변경하지 않는다. | PRD BR-004, BR-007 |
| OWN-BR-005 | friendName이나 nickname 일치를 근거로 Ownership을 자동 생성하지 않는다. | PRD BR-010 |
| OWN-BR-006 | 하나의 Blog에는 최대 한 명의 소유자만 존재한다. | PRD 12장 제외 범위 |
| OWN-BR-007 | 한 User는 MVP에서 하나의 Blog만 소유한다. | PRD 12장 제외 범위 |
| OWN-BR-008 | Ownership은 Subscription을 대체하거나 자동 생성하지 않는다. | PRD BR-001 |

---

## 7. 데이터

### Ownership

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| id | Ownership 식별자 | O |
| userId | 소유자 | O |
| blogId | 대상 Blog | O |
| verifiedAt | 인증 성공 시각 | O |
| verificationMethod | 확인에 사용된 방법 (`POST_CONTENT`, `BLOG_DESCRIPTION`) | O |

`blogId`는 유일하다. `userId`도 MVP에서는 유일하다.

### OwnershipVerification

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| id | verification 식별자 | O |
| userId | 요청자 | O |
| blogId | 대상 Blog | O |
| code | 발급된 인증 코드 | O |
| status | `PENDING` / `VERIFIED` / `EXPIRED` | O |
| expiresAt | 코드 만료 시각 | O |
| attemptCount | 확인 시도 횟수 | O |
| lastAttemptAt | 마지막 확인 시도 시각 | X |

`(userId, blogId)`에 대해 `PENDING`은 최대 하나만 존재한다.

---

## 8. API 요구사항

공통 규약은 `docs/specs/README.md`를 따른다.

### POST /api/v1/ownership/verifications

- 목적: 내 블로그 등록 시작 및 인증 코드 발급
- 인증 필요: O

Request:

```json
{
  "url": "https://velog.io/@wingwogus"
}
```

Response (201):

```json
{
  "verificationId": "own_ver_01H...",
  "blog": {
    "id": "blg_01H...",
    "title": "재현의 개발 블로그",
    "siteUrl": "https://velog.io/@wingwogus",
    "platform": "VELOG",
    "platformLabel": "Velog"
  },
  "code": "blogzip-verify-7f3a91c2",
  "expiresAt": "2026-08-31T09:12:00Z",
  "instructions": [
    "블로그에 이 코드를 포함한 글을 올려 주세요.",
    "또는 블로그 소개란에 코드를 넣어 주세요."
  ]
}
```

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `BLOG_001` | URL 형식 오류 |
| 400 | `BLOG_002` | 사설/내부 주소 |
| 404 | `BLOG_003` | 접근 불가 |
| 422 | `BLOG_004` | 새 글을 가져올 방법을 찾지 못함 |
| 409 | `OWNERSHIP_002` | 다른 User가 이미 소유 |
| 409 | `OWNERSHIP_003` | 요청자가 이미 다른 Blog를 소유 |

### POST /api/v1/ownership/verifications/{verificationId}/confirm

- 목적: 코드 확인 실행
- 인증 필요: O

Response (200) 성공:

```json
{
  "status": "VERIFIED",
  "ownership": {
    "id": "own_01H...",
    "blogId": "blg_01H...",
    "verifiedAt": "2026-08-30T10:02:00Z",
    "verificationMethod": "POST_CONTENT"
  }
}
```

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 404 | `OWNERSHIP_004` | 없거나 다른 사용자의 verification |
| 409 | `OWNERSHIP_005` | 코드 만료 |
| 409 | `OWNERSHIP_002` | 확인 사이에 다른 User가 소유 확정 |
| 422 | `OWNERSHIP_006` | Blog에서 코드를 찾지 못함 |
| 429 | `OWNERSHIP_007` | 확인 요청 제한 초과 |

### GET /api/v1/ownership/me

- 목적: 내 Blog 연결 상태 조회
- 인증 필요: O

Response (200) 연결됨:

```json
{
  "status": "VERIFIED",
  "blog": {
    "id": "blg_01H...",
    "title": "재현의 개발 블로그",
    "siteUrl": "https://velog.io/@wingwogus",
    "platform": "VELOG",
    "platformLabel": "Velog"
  },
  "verifiedAt": "2026-08-30T10:02:00Z"
}
```

Response (200) 대기 중:

```json
{
  "status": "PENDING",
  "verificationId": "own_ver_01H...",
  "blog": {
    "id": "blg_01H...",
    "title": "재현의 개발 블로그",
    "siteUrl": "https://velog.io/@wingwogus",
    "platform": "VELOG",
    "platformLabel": "Velog"
  },
  "code": "blogzip-verify-7f3a91c2",
  "expiresAt": "2026-08-31T09:12:00Z"
}
```

Response (200) 미연결:

```json
{
  "status": "NOT_CONNECTED"
}
```

### DELETE /api/v1/ownership/me

- 목적: Ownership 해제
- 인증 필요: O

Response (204): 본문 없음

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 404 | `OWNERSHIP_001` | 연결된 Blog 없음 |

---

## 9. 예외 및 실패 처리

| 상황 | 기대 동작 |
| --- | --- |
| 코드를 아직 게시하지 않고 확인 요청 | `422 OWNERSHIP_006`, verification은 `PENDING` 유지, 재시도 가능 |
| 코드 만료 후 확인 요청 | `409 OWNERSHIP_005`, 새 코드 발급 안내 |
| 확인 요청 반복 | 사용자별 제한 적용, 초과 시 `429` |
| 확인 중 블로그 접근 불가 | `404 BLOG_003`, `PENDING` 유지 |
| 두 사용자가 같은 Blog에 동시에 확인 성공 시도 | 한 명만 Ownership을 얻고 나머지는 `409 OWNERSHIP_002` |
| 이미 다른 Blog를 소유한 사용자의 등록 시도 | `409 OWNERSHIP_003`, 기존 해제 안내 |
| 사설 주소 입력 | `400 BLOG_002`, 외부 요청 없음 |
| Ownership 생성 시 기존 구독자 존재 | Subscription과 friendName을 변경하지 않는다 |
| Ownership 해제 후 재인증 | 새 코드로 다시 진행 가능, 이전 코드는 무효 |

인증 코드 원문은 실패 로그에 남기지 않는다.

---

## 10. Acceptance Criteria

- [ ] 자신의 블로그 URL로 등록을 시작하면 `201`과 인증 코드, 만료 시각, 안내 문구를 받는다.
- [ ] 코드를 게시하지 않고 확인하면 `422 OWNERSHIP_006`를 받고 상태가 `PENDING`으로 남는다.
- [ ] 코드를 블로그 글에 포함한 뒤 확인하면 `200`과 함께 Ownership이 생성된다.
- [ ] 코드를 블로그 소개란에 넣은 경우에도 확인에 성공한다.
- [ ] 같은 사용자가 같은 Blog에 등록을 재요청하면 기존 유효한 코드가 반환되고 새 verification이 생기지 않는다.
- [ ] 만료된 코드로 확인하면 `409 OWNERSHIP_005`를 받는다.
- [ ] 다른 사용자가 이미 소유한 Blog에 등록을 시작하면 `409 OWNERSHIP_002`를 받는다.
- [ ] 이미 Blog를 소유한 사용자가 다른 Blog 등록을 시작하면 `409 OWNERSHIP_003`를 받는다.
- [ ] 인증 전 상태의 Blog를 조회하는 어떤 API에도 운영자를 단정하는 필드나 공식 배지 값이 없다.
- [ ] 여러 구독자가 있는 Blog에 Ownership이 생성된 후에도 각 구독자의 Subscription과 friendName이 그대로 유지된다.
- [ ] Ownership 생성이 요청자에게 Subscription을 자동 생성하지 않는다.
- [ ] friendName이나 nickname이 실제 운영자와 일치해도 Ownership이 자동 생성되지 않는다.
- [ ] Ownership을 해제하면 `204`를 받고 Blog의 소유자 정보만 사라지며 Post와 다른 사용자 Subscription은 유지된다.
- [ ] 다른 사용자의 verificationId로 확인을 요청하면 `404 OWNERSHIP_004`를 받는다.
- [ ] 확인 요청을 제한 이상 반복하면 `429 OWNERSHIP_007`을 받는다.
- [ ] 사용자에게 보여주는 안내 문구에 "RSS", "Atom", "Feed" 문자열이 없다.

---

## 11. Open Questions

- MVP에서 지원할 확인 방법의 최종 목록 → Decision 005
- 플랫폼별 코드 확인 위치 (Naver Blog 소개란 접근 가능 여부 등) → Decision 005
- 인증 코드 만료 시간과 확인 요청 제한값 → Decision 005
- Ownership 해제 후 재인증 대기 시간을 둘지
- 다수 Blog Ownership을 언제 허용할지 (PRD Open Question)
- 소유자가 Blog URL을 변경한 경우 Ownership 유지 방식 (PRD Open Question)
