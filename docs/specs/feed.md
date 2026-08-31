# Feed Spec

## 0. 문서 정보

- Feature: Feed (Post 수집 + 피드 조회)
- Status: Draft
- Last Updated: 2026-08-30
- 관련 PRD: `docs/PRD.md` 9장 Scenario 2, 10장 친구 글 확인, 11장 MVP 범위 - Feed
- 관련 Decision: `docs/decisions/004-post-collection.md`, `docs/decisions/010-api-response-contract.md`

---

## 1. 개요

Blog.zip은 사용자가 구독한 Blog에서 새 글을 주기적으로 수집해 하나의 피드로 보여준다.

피드는 콘텐츠 발견과 구독 경험에 집중한다. 원문을 대체하지 않는다. (P-006)

피드의 주체는 블로그가 아니라 사람이다. 각 항목은 구독자가 지정한 friendName을 우선 보여준다. (P-001)

---

## 2. 범위

### In Scope

- 구독한 Blog의 Post 주기적 수집
- 중복 Post 방지
- 통합 피드 조회 (최신순)
- 특정 친구의 글만 조회
- Post 읽음 상태
- 새 게시물 표시
- 수집 실패 상태와 마지막 정상 수집 시각
- 원문 이동

### Out of Scope

- 본문 전체 저장 및 리더 뷰 제공
- 새 글 알림 (푸시, 메일)
- 북마크, Reaction, 공유
- 카테고리 분류, 추천, 요약
- 이미지 재호스팅

---

## 3. 용어

| 용어 | 정의 |
| --- | --- |
| Post | 외부 Blog에서 수집한 게시물. Blog에 속한다. |
| 수집 | Blog의 새 글 목록을 확인해 Post를 저장하는 서버 동작 |
| externalId | 원본이 제공하는 게시물 식별자. 중복 판단에 사용한다. |
| 읽음 | 사용자가 해당 Post의 원문으로 이동했거나 읽음 처리한 상태 |
| lastSuccessfulFetchAt | Blog에서 마지막으로 정상 수집에 성공한 시각 |

---

## 4. 사용자 흐름

```text
외부 Blog에 새 글 작성
↓
Blog.zip 수집 (주기 실행)
↓
새 Post 저장
↓
구독자 피드에 노출 (새 글 표시)
↓
사용자가 Post 선택
↓
원문 이동 + 읽음 처리
```

---

## 5. 기능 요구사항

### FR-001. 수집 대상

수집 대상은 최소 한 명 이상의 구독자가 있는 Blog다.

- 모든 구독이 해제된 Blog는 수집하지 않는다.
- Blog 하나를 여러 사용자가 구독해도 수집은 Blog 단위로 한 번만 한다.

### FR-002. 수집 주기

Blog별로 주기적으로 수집한다.

- 기본 주기는 30분이다. 스케줄러는 5분마다 돌며 주기가 된 Blog만 처리한다. (`docs/decisions/004-post-collection.md`)
- Subscription이 처음 생성되면 즉시 1회 수집한다.
- 연속 실패한 Blog는 재시도 간격을 점진적으로 늘린다. (30분 × 2^(연속실패-1), 최대 24시간)
- 전체 동시 수집 5, 같은 호스트 동시 1, 같은 호스트 최소 간격 1초. (`docs/decisions/004-post-collection.md`)

### FR-003. 초기 수집 범위

Subscription 생성 직후 첫 수집에서는 최근 Post만 저장한다.

- 최신 20건까지만 저장한다. (`docs/decisions/004-post-collection.md`)
- 첫 수집으로 저장된 Post는 "새 글"로 표시하지 않는다. 가입 직후 피드가 전부 새 글로 보이는 것을 막는다.

### FR-004. 중복 방지

같은 게시물을 두 번 저장하지 않는다.

- 중복 판단 키: `(blogId, externalId)`
- `externalId`가 없으면 정규화한 Post URL을 대체 키로 쓴다.
- 이미 저장된 Post의 제목이나 게시 시각이 원본에서 바뀌면 기존 레코드를 갱신한다. 새로 만들지 않는다.

### FR-005. Post 저장 항목

수집 시 다음을 저장한다.

- 제목
- 원문 URL
- 게시 시각
- 썸네일 URL (있는 경우, 원본 주소를 그대로 참조)

**본문과 발췌를 저장하지 않는다.** 피드는 누가 무엇을 썼는지 알려주고, 읽기는 원본 플랫폼에서 한다. (P-006, `docs/decisions/004-post-collection.md`)

썸네일 추출 순서는 `media:thumbnail` → `enclosure`(image)다. 없으면 `null`이며 화면은 썸네일 없는 레이아웃으로 렌더한다.

제목이 비어 있으면 Post를 저장하지 않는다.

게시 시각이 없으면 수집 시각을 사용하고, 원본 값이 아님을 구분할 수 있게 남긴다.

미래 시각으로 표기된 게시 시각은 수집 시각으로 보정한다.

### FR-006. 통합 피드 조회

로그인한 사용자는 구독한 모든 Blog의 Post를 최신순으로 조회한다.

각 항목은 다음을 포함한다.

- friendName (해당 사용자가 지정한 이름)
- Post 제목
- 게시 날짜
- 썸네일 URL (없으면 null)
- 블로그 제목과 플랫폼 표시명
- 원문 URL
- 읽음 여부
- 새 글 여부

friendName은 요청한 사용자의 Subscription에서 가져온다. 다른 사용자가 지정한 이름을 쓰지 않는다. (BR-004)

Ownership이 인증되지 않은 Blog에 대해 운영자를 단정하는 값(공식 배지 등)을 응답에 포함하지 않는다. (BR-008)

### FR-007. 친구별 피드 조회

특정 Subscription의 Post만 조회할 수 있다.

### FR-008. 새 글 표시

사용자가 아직 보지 않은 Post를 새 글로 표시한다.

- 판단 기준: 해당 Post가 읽음 처리되지 않았고, Subscription 생성 이후 수집된 Post인 경우.
- 첫 수집으로 들어온 Post는 새 글이 아니다. (FR-003)

### FR-009. 읽음 처리

사용자가 Post를 읽음으로 표시할 수 있다.

- 원문으로 이동하면 읽음으로 처리한다.
- 읽음 상태는 사용자별로 저장한다. 같은 Post를 다른 사용자가 읽어도 영향이 없다.
- 이미 읽은 Post를 다시 읽음 처리해도 성공으로 응답한다. (멱등)

### FR-010. 원문 이동

Post 선택 시 원문 URL로 이동한다.

- 목록의 모든 Post는 원본 Blog와 원문 URL을 확인할 수 있어야 한다. (BR-009)
- **Blog.zip 내부에서 원문을 대체 렌더링하지 않는다.** 웹뷰나 iframe으로 원문을 감싸지 않는다.
- 모바일은 인앱 브라우저(iOS `SFSafariViewController`, Android Custom Tabs), 웹은 새 탭으로 열고
  `rel="noopener noreferrer"`를 적용한다.
- **원문에서 돌아오면 피드의 스크롤 위치를 유지한다.**

피드는 세로 카드 스크롤로 구성한다. 레이아웃 참고는 `frontend/DESIGN.md`에 있다.

### FR-011. 수집 실패 상태

Blog별 수집 상태를 관리한다.

| 상태 | 의미 |
| --- | --- |
| `ACTIVE` | 정상 수집 중 |
| `FAILING` | 연속 실패 중이나 재시도 대상 |
| `UNAVAILABLE` | 실패가 임계값을 넘어 사실상 수집 불가 |

- 연속 실패 10회에 `UNAVAILABLE`로 전환한다. 영구 실패(404, 410)는 즉시 전환한다. (`docs/decisions/004-post-collection.md`)
- `FAILING` 이상이면 사용자에게 마지막 정상 수집 시각을 보여준다.
- 실패 상태에서도 이미 저장된 Post는 계속 보여준다.
- 실패를 조용히 삼키지 않는다. 상태와 마지막 오류 종류를 남긴다.
- 상태 화면 문구에 RSS 등 기술 용어를 쓰지 않는다. (P-002)

---

## 6. 비즈니스 규칙

| ID | 규칙 | 근거 |
| --- | --- | --- |
| FEED-BR-001 | 모든 Post는 원본 Blog와 원문 URL을 확인할 수 있어야 한다. | PRD BR-009 |
| FEED-BR-002 | 피드에 표시하는 이름은 요청한 사용자의 friendName이다. | PRD BR-004, BR-005 |
| FEED-BR-003 | 본문 전체를 저장하거나 원문 대체 뷰를 제공하지 않는다. | PRD P-006 |
| FEED-BR-004 | 같은 게시물을 중복 저장하지 않는다. | PRD 11장 Should Have |
| FEED-BR-005 | 읽음 상태는 사용자별로 독립적이다. | PRD P-007 |
| FEED-BR-006 | 수집 실패는 상태로 노출하고 조용히 무시하지 않는다. | PRD 11장 Should Have |
| FEED-BR-007 | Ownership 미인증 Blog에 운영자를 단정하는 표시를 하지 않는다. | PRD BR-008 |
| FEED-BR-008 | 사용자 화면과 API message에 RSS/Atom/Feed 용어를 쓰지 않는다. | PRD P-002 |

---

## 7. 데이터

### Post

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| id | Post 식별자 | O |
| blogId | 소속 Blog | O |
| externalId | 원본 게시물 식별자 (없으면 정규화 URL) | O |
| title | 제목 | O |
| url | 원문 URL | O |
| publishedAt | 게시 시각 | O |
| publishedAtEstimated | 게시 시각이 원본 값이 아닌 경우 true | O |
| thumbnailUrl | 썸네일 원본 주소 | X |
| collectedAt | 수집 시각 | O |

`(blogId, externalId)`는 유일하다.

### BlogFetchState

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| blogId | 대상 Blog | O |
| status | `ACTIVE` / `FAILING` / `UNAVAILABLE` | O |
| lastSuccessfulFetchAt | 마지막 정상 수집 시각 | X |
| lastAttemptAt | 마지막 시도 시각 | O |
| consecutiveFailureCount | 연속 실패 횟수 | O |
| lastFailureReason | 마지막 실패 종류 (내부 값) | X |

### PostReadState

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| userId | 사용자 | O |
| postId | 대상 Post | O |
| readAt | 읽음 처리 시각 | O |

`(userId, postId)`는 유일하다.

---

## 8. API 요구사항

공통 규약은 `docs/specs/README.md`를 따른다.

### GET /api/v1/feed

- 목적: 구독한 모든 Blog의 Post를 최신순으로 조회
- 인증 필요: O

Query:

| 이름 | 설명 |
| --- | --- |
| cursor | 페이지 커서 |
| size | 기본 20, 최대 50 |
| unreadOnly | `true`면 읽지 않은 Post만 |

Response (200):

```json
{
  "items": [
    {
      "postId": "pst_01H...",
      "title": "Spring 트랜잭션 정리",
      "url": "https://velog.io/@wingwogus/spring-tx",
      "publishedAt": "2026-08-28T02:10:00Z",
      "publishedAtEstimated": false,
      "thumbnailUrl": null,
      "isRead": false,
      "isNew": true,
      "friend": {
        "subscriptionId": "sub_01H...",
        "friendName": "지훈"
      },
      "blog": {
        "id": "blg_01H...",
        "title": "재현의 개발 블로그",
        "siteUrl": "https://velog.io/@wingwogus",
        "platform": "VELOG",
        "platformLabel": "Velog"
      }
    }
  ],
  "nextCursor": "opaque-string-or-null"
}
```

정렬은 `publishedAt` 내림차순, 동일 시각이면 `postId` 내림차순으로 안정 정렬한다.

### GET /api/v1/subscriptions/{subscriptionId}/posts

- 목적: 특정 친구의 Post만 조회
- 인증 필요: O

Response (200): `/api/v1/feed`와 동일한 항목 구조

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 404 | `POST_001` | 없거나 다른 사용자의 Subscription |

### POST /api/v1/posts/{postId}/read

- 목적: 읽음 처리
- 인증 필요: O

Response (204): 본문 없음

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 404 | `POST_001` | Post 없음 또는 구독하지 않은 Blog의 Post |

### DELETE /api/v1/posts/{postId}/read

- 목적: 읽음 해제
- 인증 필요: O

Response (204): 본문 없음

---

## 9. 예외 및 실패 처리

| 상황 | 기대 동작 |
| --- | --- |
| 수집 요청 타임아웃 | 실패로 기록, `consecutiveFailureCount` 증가, 기존 Post 유지 |
| 응답이 유효한 형식이 아님 | 실패로 기록, Post를 지우지 않음 |
| 썸네일 원본이 사라짐 | 정상 상태로 처리. 화면은 썸네일 없이 렌더 |
| 일부 항목만 파싱 실패 | 파싱 가능한 Post는 저장하고 실패 항목 수를 로그에 남김 |
| 제목 없는 항목 | 해당 항목만 건너뜀 |
| 게시 시각 없음 | 수집 시각 사용, `publishedAtEstimated: true` |
| 게시 시각이 미래 | 수집 시각으로 보정, `publishedAtEstimated: true` |
| 연속 실패가 임계값 초과 | `UNAVAILABLE`로 전환, 재시도 간격 확대, 사용자에게 마지막 정상 수집 시각 표시 |
| Blog 삭제 또는 영구 404 | `UNAVAILABLE`로 전환, 기존 Post 유지 |
| 원본에서 글이 삭제됨 | 저장된 Post를 삭제하지 않는다. 원문 이동 시 원본 404는 원본 책임이다. |
| 구독 해제 | 해당 사용자 피드에서 제외. Post와 다른 사용자 읽음 상태는 유지 |
| 구독하지 않은 Blog의 postId로 읽음 처리 | `404 POST_001` |
| 이미 읽은 Post 재요청 | `204` (멱등) |
| 구독이 0개 | `200`, `items: []`, 화면은 빈 상태로 안내 |

사용자에게 보여주는 실패 문구 예: "마지막으로 확인한 시간: 8월 28일 · 지금은 새 글을 가져올 수 없습니다."

---

## 10. Acceptance Criteria

- [ ] 구독 생성 직후 해당 Blog의 최근 Post가 수집되어 피드에 보인다.
- [ ] 첫 수집으로 저장된 Post는 `isNew: false`다.
- [ ] 같은 Blog를 두 번 수집해도 Post 수가 늘지 않는다.
- [ ] 원본에서 제목이 수정되면 기존 Post가 갱신되고 새 Post가 생기지 않는다.
- [ ] 수집 후 새로 올라온 글은 `isNew: true`로 표시된다.
- [ ] 피드는 `publishedAt` 내림차순으로 정렬되고, 같은 시각의 Post도 커서 페이징에서 중복/누락 없이 조회된다.
- [ ] 두 사용자가 같은 Blog를 각각 다른 friendName으로 구독했을 때 각자의 피드에 자신이 지정한 이름만 나온다.
- [ ] 한 사용자가 Post를 읽음 처리해도 다른 사용자의 `isRead`는 false로 유지된다.
- [ ] 읽음 처리를 두 번 호출해도 `204`를 받는다.
- [ ] 모든 피드 항목에 원문 URL과 블로그 정보가 포함된다.
- [ ] 응답에 본문이나 발췌 필드가 없다.
- [ ] 수집이 연속 실패하면 상태가 `FAILING` 또는 `UNAVAILABLE`로 바뀌고, 기존 Post는 계속 조회된다.
- [ ] 수집 실패 상태를 보여주는 응답에 마지막 정상 수집 시각이 포함된다.
- [ ] 실패 상태 관련 사용자 노출 문구에 "RSS", "Atom", "Feed" 문자열이 없다.
- [ ] 게시 시각이 없는 항목은 `publishedAtEstimated: true`로 저장된다.
- [ ] 구독이 0개인 사용자가 피드를 조회하면 `200`과 빈 목록을 받는다.
- [ ] 구독하지 않은 Blog의 postId로 읽음 처리하면 `404`를 받는다.
- [ ] 응답에 Ownership 미인증 Blog의 운영자를 단정하는 필드가 없다.

---

## 11. Open Questions

- 피드 카드의 친구 식별 표시를 무엇으로 할지 (이름 이니셜 / 블로그 favicon). `frontend/DESIGN.md` 6장
  favicon으로 가면 Blog 데이터 항목 추가가 필요하다.
- 당겨서 새로고침이 실제 수집을 트리거할지, 저장된 Post만 재조회할지
- Conditional GET(ETag, Last-Modified) 도입 여부 → Decision 004 재검토 조건
- 오래된 Post 보관 기간 및 정리 정책 (현재는 삭제하지 않음)
- 피드 정보량이 부족하다는 피드백이 반복될 경우 발췌 저장 재검토
