# Subscription Management Spec

## 0. 문서 정보

- Feature: 구독 관리 (친구 목록 조회 / 이름 수정 / 구독 해제)
- Status: Draft
- Last Updated: 2026-08-30
- 관련 PRD: `docs/PRD.md` 11장 MVP 범위 - 관리, P-001, P-007
- 관련 Decision: `docs/decisions/010-api-response-contract.md`

---

## 1. 개요

사용자가 구독 중인 친구 블로그를 확인하고 정리하는 기능이다.

목록의 주체는 블로그가 아니라 사람이다. 화면은 friendName을 우선 보여주고 블로그 정보는 출처 확인용으로 함께 노출한다. (P-001, BR-009)

friendName은 해당 사용자에게만 적용되는 Label이므로 수정은 본인 피드 표시에만 영향을 준다. (P-007, BR-004)

---

## 2. 범위

### In Scope

- 구독한 친구 목록 조회
- friendName 수정
- 구독 해제
- 목록에서 각 Blog의 수집 상태와 마지막 정상 수집 시각 표시

### Out of Scope

- Subscription 생성 → `docs/specs/blog-subscription.md`
- 피드 및 Post 조회 → `docs/specs/feed.md`
- 친구 그룹, 태그, 정렬 커스터마이즈
- 구독 일시 중지 (mute)

---

## 3. 용어

| 용어 | 정의 |
| --- | --- |
| 친구 목록 | 로그인한 사용자의 Subscription 목록 |
| friendName | 구독자가 지정한 이름. 구독자에게만 적용되는 Label이다. |
| 수집 상태 | 해당 Blog의 `BlogFetchState.status` (`ACTIVE` / `FAILING` / `UNAVAILABLE`) |

---

## 4. 사용자 흐름

```text
친구 목록
↓
친구 선택
↓
이름 수정 또는 구독 해제
↓
목록 및 피드 반영
```

---

## 5. 기능 요구사항

### FR-001. 친구 목록 조회

로그인한 사용자는 자신의 Subscription 목록을 조회한다.

각 항목은 다음을 포함한다.

- subscriptionId
- friendName
- 블로그 제목, 사용자에게 보여줄 주소, 플랫폼 표시명
- 마지막 게시물의 게시 시각 (없으면 null)
- 읽지 않은 Post 수 (최대 99까지 세고, 초과하면 99를 반환한다)
- 수집 상태와 마지막 정상 수집 시각

기본 정렬은 마지막 게시물 시각 내림순이다. 게시물이 없는 항목은 뒤로 보낸다.

응답에 `feedUrl`을 포함하지 않는다. (P-002)

Ownership 미인증 Blog에 운영자를 단정하는 필드를 포함하지 않는다. (BR-008)

### FR-002. friendName 수정

사용자는 자신의 Subscription의 friendName을 수정할 수 있다.

- 검증 규칙은 생성과 같다. 앞뒤 공백 제거 후 1자 이상 20자 이하.
- 수정은 요청자의 Subscription에만 반영된다. 같은 Blog를 구독한 다른 사용자에게 영향이 없다. (BR-004, BR-005)
- Blog 레코드와 Ownership에 영향을 주지 않는다. (BR-010)
- 같은 값으로 수정해도 성공으로 응답한다.
- 다른 Subscription과 같은 friendName을 허용한다. 한 친구가 블로그를 여러 개 운영할 수 있다.

### FR-003. 구독 해제

사용자는 목록에서 구독을 해제할 수 있다.

- 동작과 효과는 `docs/specs/blog-subscription.md` FR-006과 같다.
- 해당 사용자의 피드에서 그 Blog의 Post가 제외된다.
- Blog, Post, 다른 사용자의 Subscription과 읽음 상태는 유지된다.
- 그 Blog를 구독한 사용자가 아무도 남지 않으면 이후 수집 대상에서 제외된다. (`docs/specs/feed.md` FR-001)

### FR-004. 수집 실패 표시

수집 상태가 `FAILING` 또는 `UNAVAILABLE`인 항목은 목록에서 구분해 보여준다.

- 마지막 정상 수집 시각을 함께 표시한다.
- 실패 상태여도 항목을 목록에서 숨기지 않는다.
- 노출 문구에 RSS 등 기술 용어를 쓰지 않는다. (P-002)

---

## 6. 비즈니스 규칙

| ID | 규칙 | 근거 |
| --- | --- | --- |
| SUBM-BR-001 | friendName 수정은 요청자의 Subscription에만 적용된다. | PRD BR-004, BR-005 |
| SUBM-BR-002 | friendName 수정이 Blog나 Ownership에 영향을 주지 않는다. | PRD BR-010 |
| SUBM-BR-003 | 구독 해제가 Blog와 Post를 삭제하지 않는다. | PRD BR-007 |
| SUBM-BR-004 | 목록에서 각 항목의 원본 Blog를 확인할 수 있어야 한다. | PRD BR-009 |
| SUBM-BR-005 | Ownership 미인증 Blog에 운영자를 단정하는 표시를 하지 않는다. | PRD BR-008 |
| SUBM-BR-006 | 응답과 화면에 RSS/Atom/Feed URL을 노출하지 않는다. | PRD P-002 |

---

## 7. 데이터

이 기능은 새 데이터를 만들지 않는다.

- `Subscription` → `docs/specs/blog-subscription.md` 7장
- `BlogFetchState`, `PostReadState` → `docs/specs/feed.md` 7장

---

## 8. API 요구사항

공통 규약은 `docs/specs/README.md`를 따른다.

### GET /api/v1/subscriptions

- 목적: 친구 목록 조회
- 인증 필요: O

Query:

| 이름 | 설명 |
| --- | --- |
| cursor | 페이지 커서 |
| size | 기본 20, 최대 50 |

Response (200):

```json
{
  "items": [
    {
      "subscriptionId": "sub_01H...",
      "friendName": "지훈",
      "blog": {
        "id": "blg_01H...",
        "title": "재현의 개발 블로그",
        "siteUrl": "https://velog.io/@wingwogus",
        "platform": "VELOG",
        "platformLabel": "Velog"
      },
      "lastPostPublishedAt": "2026-08-28T02:10:00Z",
      "unreadCount": 3,
      "fetchStatus": "ACTIVE",
      "lastSuccessfulFetchAt": "2026-08-30T08:00:00Z",
      "createdAt": "2026-08-20T11:00:00Z"
    }
  ],
  "nextCursor": null
}
```

### PATCH /api/v1/subscriptions/{subscriptionId}

- 목적: friendName 수정
- 인증 필요: O

Request:

```json
{
  "friendName": "지훈이"
}
```

Response (200):

```json
{
  "subscriptionId": "sub_01H...",
  "friendName": "지훈이"
}
```

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `COMMON_001` | friendName 규칙 위반 |
| 404 | `SUBSCRIPTION_001` | 없거나 다른 사용자의 Subscription |

### DELETE /api/v1/subscriptions/{subscriptionId}

구독 해제 API는 `docs/specs/blog-subscription.md` 8장에 정의되어 있다. 이 Spec에서 계약을 다시 정의하지 않는다.

---

## 9. 예외 및 실패 처리

| 상황 | 기대 동작 |
| --- | --- |
| 구독이 0개 | `200`, `items: []`, 화면은 블로그 추가를 안내하는 빈 상태 |
| 읽지 않은 Post가 99개를 초과 | `unreadCount: 99`. 화면은 `99+`로 표시한다 |
| friendName이 공백만 | `400 COMMON_001` |
| friendName이 20자 초과 | `400 COMMON_001` |
| 다른 사용자의 subscriptionId로 수정 | `404 SUBSCRIPTION_001` (존재 여부 노출 금지) |
| 이미 해제된 subscriptionId로 수정 | `404 SUBSCRIPTION_001` |
| 같은 값으로 수정 | `200` |
| 수집 상태가 `UNAVAILABLE` | 항목을 유지하고 마지막 정상 수집 시각을 함께 표시 |
| 게시물이 아직 없는 Blog | `lastPostPublishedAt: null`, 정렬에서 뒤로 배치 |

---

## 10. Acceptance Criteria

- [ ] 구독이 여러 개일 때 마지막 게시물 시각 내림순으로 조회되고, 게시물이 없는 항목이 뒤에 온다.
- [ ] 각 항목에 friendName, 블로그 제목, 블로그 주소, 플랫폼 표시명이 포함된다.
- [ ] 응답에 `feedUrl`이 포함되지 않는다.
- [ ] 각 항목의 `unreadCount`가 해당 사용자 기준으로 계산된다. 다른 사용자가 읽어도 값이 변하지 않는다.
- [ ] friendName을 수정하면 `200`을 받고 목록과 피드에 새 이름이 반영된다.
- [ ] 같은 Blog를 구독한 다른 사용자의 friendName은 수정 후에도 변하지 않는다.
- [ ] friendName 수정 후 해당 Blog의 Ownership 상태와 소유자 정보가 변하지 않는다.
- [ ] 공백만 입력하거나 20자를 초과하면 `400 COMMON_001`를 받고 값이 바뀌지 않는다.
- [ ] 다른 사용자의 subscriptionId로 수정하면 `404`를 받는다.
- [ ] 구독을 해제하면 내 피드에서 해당 Blog의 Post가 사라지고, 같은 Blog를 구독한 다른 사용자의 피드는 그대로 유지된다.
- [ ] 구독을 해제해도 Blog와 Post 레코드가 삭제되지 않는다.
- [ ] 수집 상태가 `FAILING` 또는 `UNAVAILABLE`인 항목도 목록에 남고 마지막 정상 수집 시각이 반환된다.
- [ ] 구독이 0개인 사용자가 조회하면 `200`과 빈 목록을 받는다.
- [ ] 목록 관련 사용자 노출 문구에 "RSS", "Atom", "Feed" 문자열이 없다.

---

## 11. Open Questions

- 구독 일시 중지(mute)를 도입할지
- 친구와 일반 관심 Blog를 목록에서 구분할지 (PRD Open Question)
