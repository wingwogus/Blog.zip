# Blog Subscription Spec

## 0. 문서 정보

- Feature: 친구 블로그 추가 (Blog 탐색 + Subscription 생성)
- Status: Draft
- Last Updated: 2026-08-30
- 관련 PRD: `docs/PRD.md` 9장 Scenario 1, 10장 친구 블로그 추가, 11장 MVP 범위 - 블로그 구독
- 관련 Decision: `docs/decisions/003-blog-discovery.md`, `docs/decisions/010-api-response-contract.md`

---

## 1. 개요

사용자가 친구의 블로그 URL을 입력하면 Blog.zip이 해당 블로그를 식별하고 새 글을 가져올 수 있는지 확인한 뒤, "누구의 블로그인가요?"에 답한 이름으로 구독 관계를 만든다.

이 기능은 Blog.zip의 진입점이며 제품 핵심 리스크가 몰려 있는 지점이다. 사용자는 RSS 주소를 알지 못하고, 입력하는 URL은 블로그 홈, 특정 글, 모바일 주소 등 무엇이든 될 수 있다.

사용자 경험에서 RSS/Atom을 노출하지 않는다. (P-002)

---

## 2. 범위

### In Scope

- 블로그 URL 입력 및 검증
- 지원 가능한 Blog인지 확인 (Feed 탐색)
- 블로그 기본 정보 조회 (제목, 플랫폼, 대표 URL)
- 미리보기용 최근 글 목록
- 친구 이름 지정
- Subscription 생성
- Subscription 삭제
- 동일 Blog 중복 구독 방지

### Out of Scope

- Feed 없는 블로그의 HTML 크롤링 지원 (`docs/decisions/003-blog-discovery.md`에서 미지원으로 확정)
- 비공개 블로그 지원
- Post 수집 스케줄링 및 피드 조회 → `docs/specs/feed.md`
- 친구 이름 수정 및 목록 조회 → `docs/specs/subscription-management.md`
- 소유권 인증 → `docs/specs/blog-ownership.md`

---

## 3. 용어

| 용어 | 정의 |
| --- | --- |
| 입력 URL | 사용자가 입력한 문자열. 블로그 홈, 글 상세, 모바일 주소일 수 있다. |
| Blog | Blog.zip이 콘텐츠를 수집하는 외부 블로그. 사용자와 무관하게 하나만 존재한다. |
| feedUrl | Blog에서 새 글을 가져오는 주소. 내부 값이며 사용자에게 노출하지 않는다. |
| canonicalUrl | 중복 판단에 사용하는 Blog의 정규화된 대표 주소 |
| Subscription | User와 Blog 사이의 개인 구독 관계 |
| friendName | 구독자가 해당 Blog에 붙인 이름. 구독자에게만 적용되는 Label이다. |

---

## 4. 사용자 흐름

```text
Home
↓
친구 블로그 추가
↓
블로그 URL 입력
↓
Blog 탐색 (서버)
↓
블로그 정보 + 최근 글 미리보기 확인
↓
"누구의 블로그인가요?"
↓
이름 입력
↓
Subscription 생성
↓
친구 목록 / Feed에 추가
```

탐색과 생성은 두 단계로 나눈다. 사용자가 이름을 입력하기 전에 "이 블로그가 맞다"를 확인할 수 있어야 한다.

---

## 5. 기능 요구사항

### FR-001. URL 입력 검증

입력 URL은 신뢰할 수 없는 외부 입력으로 다룬다. 탐색 전에 다음을 검증한다.

- scheme이 없으면 `https://`를 붙여 해석한다.
- 최종 scheme은 `http` 또는 `https`만 허용한다.
- 최대 길이 2048자.
- 호스트가 없으면 거부한다.
- 사설 IP, 루프백, 링크 로컬, 내부 도메인 주소는 거부한다. 리다이렉트를 따라간 결과도 같은 규칙으로 검증한다.

### FR-002. Blog 탐색

서버는 입력 URL에서 Blog와 새 글 수집 경로를 찾는다.

탐색 순서:

1. 알려진 플랫폼 패턴으로 판별한다. (Naver Blog, Velog, Tistory)
2. 문서의 `<link rel="alternate">`에서 Feed 주소를 찾는다.
3. 관용 경로를 시도한다. (`/rss`, `/feed`, `/atom.xml`, `/rss.xml`, `/index.xml`)
4. 위 과정에서 찾은 주소가 유효한 Feed 문서인지 파싱해 확인한다.

- 글 상세 URL을 입력해도 블로그 단위로 정규화해 탐색한다.
- 지원 판단 기준은 플랫폼 이름이 아니라 유효한 RSS 또는 Atom Feed를 찾을 수 있는지 하나다. 플랫폼별 규칙과 요청 상한은 `docs/decisions/003-blog-discovery.md`에서 관리한다.
- 탐색 결과가 여러 개면 전체 글 Feed를 우선한다. 카테고리, 댓글 Feed는 선택하지 않는다.
- HTML에 선언된 Feed 주소가 URL 문법에 맞지 않거나 HTTP(S) 주소가 아니면 해당 후보만 제외하고 나머지 후보 탐색을 계속한다.

### FR-003. 블로그 정보 조회

탐색에 성공하면 다음을 응답한다.

- 블로그 제목
- 플랫폼 표시명 (예: `네이버 블로그`, `Velog`, `Tistory`, `개인 블로그`)
- 사용자에게 보여줄 블로그 주소
- 최근 글 최대 3건 (제목, 게시 날짜)
- 이미 구독 중인지 여부
- 이 사용자가 지정한 기존 friendName (구독 중인 경우)

블로그 제목이 없으면 호스트명을 대신 사용한다.

최근 글 미리보기는 "이 블로그가 맞는지" 확인용이다. `feedUrl`은 응답에 포함하지 않는다. (P-002)

### FR-004. Subscription 생성

사용자는 탐색된 Blog에 friendName을 지정해 구독을 만든다.

- `friendName`: 앞뒤 공백 제거 후 1자 이상 20자 이하.
- 동일 Blog를 이미 구독 중이면 새로 만들지 않는다.
- 같은 사용자가 서로 다른 Blog에 같은 friendName을 쓸 수 있다. (한 친구가 블로그를 두 개 운영할 수 있다)
- 서로 다른 사용자가 같은 Blog에 다른 friendName을 쓸 수 있다. (BR-005)
- Subscription 생성은 소유권을 의미하지 않으며 소유권 인증을 요구하지 않는다. (BR-001, BR-002)
- 대상 Blog 운영자의 Blog.zip 가입 여부를 확인하지 않으며, 가입 여부가 생성 조건이 되지도 않는다. (P-004, BR-006)
- 사용자당 구독 수에 상한을 두지 않는다. 남용이 관측되면 그때 제한을 검토한다.

생성 직후 해당 Blog의 최근 Post를 수집 대기 상태로 등록한다. 수집 동작은 `docs/specs/feed.md`에서 정한다.

### FR-005. Blog 레코드 재사용

같은 Blog를 여러 사용자가 구독할 때 Blog 레코드는 하나만 유지한다.

- 판단 기준은 `canonicalUrl`이다.
- 정규화 규칙: 소문자 호스트, 기본 포트 제거, 후행 슬래시 제거, `www.` 및 모바일 서브도메인(`m.`) 제거, 추적 쿼리 파라미터 제거.
- 기존 Blog가 있으면 재사용하고 friendName만 Subscription에 저장한다.

### FR-006. Subscription 삭제

사용자는 구독을 해제할 수 있다.

- 해당 사용자의 피드에서 그 Blog의 Post가 사라진다.
- Blog와 Post 레코드는 삭제하지 않는다. 다른 구독자가 있을 수 있다.
- 같은 Blog를 다시 구독하면 새 Subscription으로 만든다. 이전 friendName을 복원하지 않는다.

---

## 6. 비즈니스 규칙

| ID | 규칙 | 근거 |
| --- | --- | --- |
| SUB-BR-001 | 공개 접근 가능한 Blog는 소유권 인증 없이 구독할 수 있다. | PRD BR-002 |
| SUB-BR-002 | Subscription은 Ownership을 의미하지 않는다. | PRD BR-001 |
| SUB-BR-003 | friendName은 입력한 사용자에게만 적용된다. | PRD BR-004 |
| SUB-BR-004 | 동일 Blog를 다른 사용자가 다른 이름으로 저장할 수 있다. | PRD BR-005 |
| SUB-BR-005 | Blog 운영자의 가입 여부와 무관하게 구독할 수 있다. | PRD P-004, BR-006 |
| SUB-BR-006 | friendName을 근거로 Blog 소유자를 확정하지 않는다. Ownership에 영향을 주지 않는다. | PRD BR-010 |
| SUB-BR-007 | 한 User는 같은 Blog를 두 번 구독할 수 없다. | PRD 11장 Should Have |
| SUB-BR-008 | 응답과 화면에 RSS, Atom, Feed URL을 노출하지 않는다. | PRD P-002 |

---

## 7. 데이터

### Blog

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| id | Blog 식별자 | O |
| canonicalUrl | 중복 판단 기준이 되는 정규화된 주소 | O |
| siteUrl | 사용자에게 보여줄 블로그 주소 | O |
| feedUrl | 새 글 수집에 사용하는 내부 주소 | O |
| title | 블로그 제목 | O |
| platform | 플랫폼 구분값 (`NAVER`, `VELOG`, `TISTORY`, `GENERIC`) | O |
| ownerUserId | Ownership이 인증된 경우에만 채운다 | X |

### Subscription

| 항목 | 의미 | 필수 |
| --- | --- | --- |
| id | Subscription 식별자 | O |
| userId | 구독자 | O |
| blogId | 대상 Blog | O |
| friendName | 구독자가 지정한 이름 (구독자 전용 Label) | O |
| createdAt | 구독 시각 | O |

`(userId, blogId)`는 유일하다.

`friendName`은 Blog에 저장하지 않는다. (BR-004, BR-005)

---

## 8. API 요구사항

공통 규약은 `docs/specs/README.md`를 따른다.

### POST /api/v1/blogs/lookup

- 목적: 입력 URL로 Blog를 탐색하고 확인 정보를 반환한다. 저장하지 않는다.
- 인증 필요: O

Request:

```json
{
  "url": "velog.io/@wingwogus"
}
```

Response (200):

```json
{
  "blog": {
    "title": "재현의 개발 블로그",
    "siteUrl": "https://velog.io/@wingwogus",
    "platform": "VELOG",
    "platformLabel": "Velog"
  },
  "recentPosts": [
    { "title": "Spring 트랜잭션 정리", "publishedAt": "2026-08-28T02:10:00Z" }
  ],
  "alreadySubscribed": false,
  "currentFriendName": null,
  "lookupToken": "lkp_01H..."
}
```

`lookupToken`은 탐색 결과를 생성 요청까지 이어주는 단기 토큰이다. 발급한 사용자만 사용할 수 있고 만료 시간은 10분이다. 클라이언트가 내부 `feedUrl`을 다루지 않게 하려는 값이다.

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `BLOG_001` | URL 형식 오류, 허용되지 않는 scheme, 길이 초과 |
| 400 | `BLOG_002` | 사설/내부 주소 |
| 404 | `BLOG_003` | 응답 없음, 4xx/5xx, 타임아웃 |
| 422 | `BLOG_004` | 접근은 되지만 새 글을 가져올 방법을 찾지 못함 |
| 429 | `BLOG_006` | 탐색 요청 제한 초과 |

### POST /api/v1/subscriptions

- 목적: Subscription 생성
- 인증 필요: O

Request:

```json
{
  "lookupToken": "lkp_01H...",
  "friendName": "지훈"
}
```

Response (201):

```json
{
  "id": "sub_01H...",
  "friendName": "지훈",
  "blog": {
    "id": "blg_01H...",
    "title": "재현의 개발 블로그",
    "siteUrl": "https://velog.io/@wingwogus",
    "platform": "VELOG",
    "platformLabel": "Velog"
  },
  "createdAt": "2026-08-30T09:12:00Z"
}
```

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `COMMON_001` | friendName 규칙 위반 |
| 400 | `BLOG_005` | lookupToken 만료 또는 무효 |
| 409 | `SUBSCRIPTION_002` | 이미 같은 Blog를 구독 중 |

### DELETE /api/v1/subscriptions/{subscriptionId}

- 목적: 구독 해제
- 인증 필요: O

Response (204): 본문 없음

Error:

| HTTP | code | 상황 |
| --- | --- | --- |
| 404 | `SUBSCRIPTION_001` | 없거나 다른 사용자의 Subscription |

---

## 9. 예외 및 실패 처리

| 상황 | 기대 동작 |
| --- | --- |
| 오타 등으로 접근 불가 | `404 BLOG_003`, "블로그에 연결할 수 없습니다. 주소를 확인해 주세요." |
| 블로그는 열리지만 새 글 목록을 찾지 못함 | `422 BLOG_004`, "아직 이 블로그는 지원하지 않습니다." RSS 용어를 쓰지 않는다. |
| 글 상세 URL 입력 | 블로그 단위로 정규화해 탐색 성공 |
| 모바일 주소(`m.blog.naver.com`) 입력 | 데스크톱 주소와 같은 Blog로 판단 |
| 사설 IP 또는 `localhost` 입력 | `400 BLOG_002`, 요청을 보내지 않는다 |
| 리다이렉트가 사설 주소로 향함 | 중단하고 `400 BLOG_002` |
| 응답이 과도하게 큼 | 상한까지만 읽고 초과 시 `422 BLOG_004` |
| 탐색 타임아웃 | `404 BLOG_003`, 재시도 안내 |
| 이미 구독 중인 Blog 탐색 | `200` + `alreadySubscribed: true`, 화면에서 생성 대신 기존 구독을 안내 |
| 이미 구독 중인데 생성 요청 | `409 SUBSCRIPTION_002` |
| lookupToken 만료 후 생성 | `400 BLOG_005`, 화면은 URL 입력 단계로 되돌린다 |
| 탐색 요청 반복 | 사용자별 요청 제한 적용, 초과 시 `429` |

외부 요청 상한은 연결 3초, 응답 5초, 탐색 1건 전체 10초, 본문 2MB, 리다이렉트 3회다. (`docs/decisions/003-blog-discovery.md`)

탐색 실패를 조용히 삼키지 않는다. 실패 원인을 구분해 응답하고 서버 로그에 남긴다.

---

## 10. Acceptance Criteria

- [ ] Velog, Tistory, Naver Blog, RSS를 제공하는 개인 블로그의 홈 URL로 탐색하면 제목과 플랫폼, 최근 글이 반환된다.
- [ ] 글 상세 URL을 입력해도 같은 Blog로 탐색된다.
- [ ] `m.blog.naver.com/...`과 `blog.naver.com/...`이 같은 Blog로 판단된다.
- [ ] `http://localhost:8080`, `http://127.0.0.1`, 사설 IP 입력 시 외부 요청 없이 `400 BLOG_002`을 받는다.
- [ ] 공개 외부 주소로 시작해 사설 주소로 리다이렉트되면 `400 BLOG_002`을 받는다.
- [ ] Feed가 없는 사이트는 `422 BLOG_004`를 받고, 응답 message에 "RSS", "Atom", "Feed" 문자열이 없다.
- [ ] lookup과 subscription 응답 어디에도 `feedUrl`이 포함되지 않는다.
- [ ] friendName을 지정해 생성하면 `201`을 받고 친구 목록과 피드에 나타난다.
- [ ] 같은 Blog를 다시 구독하려 하면 `409 SUBSCRIPTION_002`를 받는다.
- [ ] 서로 다른 두 사용자가 같은 Blog를 각각 다른 friendName으로 구독할 수 있고, 각자 자신이 지정한 이름만 본다.
- [ ] 두 사용자가 같은 Blog를 구독했을 때 Blog 레코드는 하나만 생성된다.
- [ ] 구독을 해제하면 내 피드에서 해당 Blog의 Post가 사라지고, 같은 Blog를 구독한 다른 사용자의 피드는 영향을 받지 않는다.
- [ ] Ownership이 없는 상태에서도 구독 생성 전 과정이 성공한다.
- [ ] 다른 사용자의 subscriptionId로 삭제를 요청하면 `404`를 받는다.

---

## 11. Open Questions

- Blog URL이 바뀐 경우 Migration 정책 (PRD Open Question)
