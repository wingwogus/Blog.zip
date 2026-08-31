# Blog.zip Feed 디자인 참고

## 0. 문서 정보

- Status: Draft
- Last Updated: 2026-08-30
- 대상: Feed 화면 (카드 스크롤)
- 관련 문서: `docs/PRD.md` 7장, `docs/specs/feed.md`, `frontend/AGENTS.md`

이 문서는 **디자인 참고 자료**다. 기능 동작과 완료 조건은 `docs/specs/feed.md`가 Source of Truth다.
둘이 충돌하면 Spec을 먼저 확인한다.

---

## 1. 방향

**인스타그램식 카드 스크롤.** 세로로 계속 내리면서 친구들의 새 글을 발견하고, 읽고 싶은 글은 원문으로 이동한다.

인앱 열람(웹뷰, 리더 뷰)은 채택하지 않았다. 이유는 아래 3장에 있다.

### 무엇을 참고하고 무엇을 참고하지 않는가

인스타그램에서 가져올 것은 **레이아웃 원칙**이다.

Meta의 Instagram 브랜드 가이드는 레이아웃 방향을 "Simple. Flexible. Content-first."로 정의하고,
"full-bleed images reference the in-app experience and give maximum attention to content"라고 설명한다.
([Instagram brand - Layout](https://about.instagram.com/brand/layout))

2026년 브랜드 리프레시에서도 같은 방향이 유지된다.
"The refreshed direction creates more room to breathe, and is built so the community's work is what carries the energy."
([Meta Design - The new Instagram brand identity](https://www.meta.com/design-at-meta/blog/the-new-instagram-brand-identity/))

우리에게 옮기면 이렇게 된다.

| 인스타그램 | Blog.zip |
| --- | --- |
| 콘텐츠(사진)가 주인공, UI는 물러남 | 친구 이름과 글 제목이 주인공 |
| 이미지 full-bleed | 썸네일이 카드 폭을 꽉 채움 |
| 여백으로 호흡 | 카드 사이 여백으로 글 단위를 구분 |

가져오지 않을 것:

- **인스타그램의 시각적 아이덴티티.** 그라디언트, Instagram Sans, 로고 형태는 Meta의 상표이며
  브랜드 가이드에 사용 제약이 있다. 우리 앱에 쓰지 않는다.
  ([Instagram brand assets and guidelines](https://www.meta.com/brand/resources/instagram/instagram-brand/))
- **좋아요, 댓글, 공유 액션 바.** PRD 5장 Non-Goals에서 원본 플랫폼의 반응 기능을 통합하지 않기로 했다.
  카드 하단에 액션 아이콘 줄을 만들지 않는다. MVP에서 카드의 유일한 액션은 원문 이동이다.
- **스토리 트레이, 릴스, 탐색 탭.** MVP 범위가 아니다.

인스타그램은 앱 UI용 공개 디자인 시스템(컴포넌트 규격, 토큰)을 배포하지 않는다.
따라서 아래 수치는 인스타그램의 실제 구현값이 아니라, 위 원칙과 플랫폼 가이드라인(Apple HIG, Material)에서
우리가 정한 값이다. **구현 시 실제 화면에서 조정하는 것을 전제로 한다.**

---

## 2. 카드 구조

한 카드 = 한 개의 Post. 카드 하나가 "누가 언제 무슨 글을 썼다"를 전달한다.

```text
┌─────────────────────────────────┐
│ (아바타)  지훈            2시간 전 │  헤더
│           Velog                 │
├─────────────────────────────────┤
│                                 │
│          썸네일 (4:5)            │  이미지 (있을 때만)
│                                 │
├─────────────────────────────────┤
│ Spring 트랜잭션 전파 옵션 정리      │  제목 (최대 3줄)
│                                 │
│ velog.io                    →   │  출처 + 이동
└─────────────────────────────────┘
```

### 헤더

| 요소 | 내용 | 근거 |
| --- | --- | --- |
| 이름 | `friend.friendName` | PRD P-001, BR-004 |
| 시각 | `publishedAt`의 상대 시간 | `feed.md` FR-006 |
| 플랫폼 | `blog.platformLabel` | `feed.md` FR-006 |

- **이름이 가장 크고 굵다.** 이 화면의 주체는 블로그가 아니라 사람이다. (P-001)
- `publishedAtEstimated`가 `true`면 "약 2시간 전"처럼 추정임을 표시한다. 원본이 게시 시각을 주지 않은 경우다.
- 플랫폼은 보조 정보다. 이름보다 작고 흐리게. 다만 **숨기지 않는다.** 출처 확인이 BR-009 요구사항이다.
- 아바타 자리는 아래 6장 참고. 아직 데이터가 없다.

### 썸네일

- 비율: **4:5 세로**를 컨테이너 기준으로 한다.
  4:5는 모바일 피드에서 세로 공간을 가장 많이 차지하는 비율로 널리 권장된다.
  ([Instagram aspect ratio guide](https://growthscribe.com/aspect-ratio-for-instagram/),
  [Instagram post size guide](https://recurpost.com/instagram-scheduler/instagram-post-size-guide/))
- **원본 이미지 비율을 우리가 통제할 수 없다.** 썸네일은 외부 블로그 RSS에서 온 임의 크기 이미지다.
  고정 비율 컨테이너 + `object-fit: cover`(중앙 크롭)로 처리한다. 레이아웃이 이미지마다 흔들리면 스크롤이 불편해진다.
- 카드 폭을 꽉 채운다. 좌우 여백을 주지 않는다. (full-bleed)
- `thumbnailUrl`이 `null`이면 이미지 영역을 아예 만들지 않는다. 회색 플레이스홀더를 넣지 않는다.
  플레이스홀더는 "이미지가 로딩 중"으로 오해된다. 제목 중심 카드로 자연스럽게 좁아지는 편이 낫다.
- **원본 이미지가 사라지면 로드에 실패한다.** 이건 정상 상태다. (`docs/decisions/004-post-collection.md`)
  실패 시 이미지 영역을 접고 제목 중심 카드로 폴백한다. 깨진 이미지 아이콘을 노출하지 않는다.

썸네일 확보율은 플랫폼마다 다르다. 실제 측정값:

```text
naver   50/50 항목에 이미지
velog   15/17
개인 블로그(github.blog)  5/10
```

즉 **이미지 없는 카드가 정상적으로 자주 나온다.** 이미지 없는 상태를 예외가 아니라 기본 케이스 중 하나로 디자인한다.

### 본문

**본문과 발췌를 표시하지 않는다.** 저장하지 않기 때문이다. (`docs/decisions/004-post-collection.md`)

RSS의 본문 제공 수준을 실제로 재보면 플랫폼마다 0자에서 전문까지 갈린다.
네이버는 중간값 3자다. 일관된 미리보기를 만들 수 없어서 아예 두지 않기로 했다.

카드가 전달하는 정보는 **누가 / 언제 / 무슨 제목 / 어디에**까지다.

### 제목

- 카드에서 가장 많은 시각적 비중을 차지하는 텍스트.
- 최대 3줄, 넘치면 말줄임.
- 원문 제목을 그대로 쓴다. 우리가 다시 쓰거나 요약하지 않는다. (P-006)

### 하단

- 블로그 호스트 또는 블로그 제목 + 이동을 암시하는 표시.
- **카드 전체가 탭 영역이다.** 하단 링크만 누를 수 있게 만들지 않는다.

---

## 3. 원문 이동

카드를 탭하면 원문으로 간다. **앱 안에서 본문을 렌더링하지 않는다.**

이유:

- 네이버 블로그 포스트 페이지 자체가 `frameset` + `iframe` 구조라 우리 iframe에 넣으면 중첩이 되고
  모바일 스크롤 제어가 사실상 불가능하다.
- 티스토리는 `X-Frame-Options: "allow-from tistory.com"`을 보낸다. `allow-from`은 폐기된 지시어이고
  Chrome은 지원하지 않는다. 플랫폼마다 결과가 갈린다.
- 개인 블로그는 프레임을 허용할 이유가 없다. github.blog는 `SAMEORIGIN`이다.
  "RSS만 있으면 지원"이라는 범위(`docs/decisions/003-blog-discovery.md`)가 깨진다.
- PRD P-006이 원본 플랫폼을 대체하지 않는다고 정하고 있다.

대신 **이탈감을 줄이는 데 집중한다.**

| 환경 | 방식 |
| --- | --- |
| iOS | `SFSafariViewController` |
| Android | Custom Tabs |
| Web | 새 탭 (`rel="noopener noreferrer"`) |

인앱 브라우저는 앱을 벗어나는 느낌 없이 열리고 닫으면 원래 자리로 돌아온다.

돌아왔을 때 반드시 지킬 것:

- **스크롤 위치 유지.** 이게 깨지면 "앱 안에서 본다"는 감각이 무너진다. 카드 스크롤의 핵심 요구사항이다.
- 읽음 표시 반영. 원문으로 이동하면 읽음으로 처리된다. (`feed.md` FR-009)

---

## 4. 상태 표현

### 새 글

- `isNew: true`인 카드를 구분한다.
- 표현은 **테두리나 배경 톤 같은 은근한 방식**을 쓴다. 빨간 점 배지처럼 알림 성격이 강한 표시는 피한다.
  이 제품은 밀린 알림을 처리하는 도구가 아니라 둘러보는 피드다.
- 첫 수집으로 들어온 글은 `isNew: false`다. 구독 직후 피드가 전부 새 글로 보이지 않게 한 결정이다.
  (`docs/decisions/004-post-collection.md`)

### 읽음

- `isRead: true`면 카드를 시각적으로 가라앉힌다. 제목 색을 낮추는 정도.
- **숨기지 않는다.** 읽은 글도 스크롤에 남아 있어야 다시 찾을 수 있다.

### 수집 실패

`fetchStatus`가 `FAILING` / `UNAVAILABLE`인 블로그는 친구 목록에서 마지막 정상 수집 시각을 보여준다.
(`docs/specs/subscription-management.md` FR-004)

문구 예:

```text
마지막으로 확인한 시간: 8월 28일
지금은 새 글을 가져올 수 없습니다
```

**"RSS", "Atom", "Feed", "피드 주소" 같은 단어를 쓰지 않는다.** (PRD P-002)
서버가 내려주는 문구도 같은 규칙을 따르며 테스트로 검증한다. (`docs/decisions/010-api-response-contract.md`)

### 빈 상태

구독이 0개일 때 (`items: []`):

- 빈 화면을 그대로 두지 않는다. "친구 블로그 추가"로 유도한다.
- 가입 직후 바로 도달하는 화면이다. 여기서 막히면 P-005(낮은 초기 진입장벽)가 무의미해진다.

### 로딩

- 첫 로딩: 카드 형태의 스켈레톤. 화면 전체 스피너를 쓰지 않는다.
- 추가 로딩(스크롤 하단): 하단에 작은 인디케이터. 기존 카드를 가리지 않는다.
- 무한 스피너 상태를 만들지 않는다. 실패하면 재시도 가능한 상태로 전환한다. (`frontend/AGENTS.md`)

---

## 5. 레이아웃 수치

플랫폼 가이드라인에 근거가 있는 것과 우리가 정한 것을 구분한다.

### 근거가 있는 것

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 최소 탭 영역 | 44pt (iOS) / 48dp (Android) | Apple HIG, Material Design |
| 썸네일 기준 비율 | 4:5 | 모바일 피드에서 세로 점유 최대 |

WCAG 2.2 SC 2.5.8은 최소 24x24px를 Level AA로 요구하고, Apple은 44x44pt, Google은 48x48dp를 권장한다.
([Touch target size and mobile accessibility](https://buildwithaccess.com/blog/touch-target-size-mobile-accessibility-wcag))

카드 전체가 탭 영역이므로 이 제약은 자연히 만족한다. 다만 카드 안에 별도 버튼을 넣는다면 반드시 확인한다.

### 우리가 정한 것 (구현 시 조정)

| 항목 | 값 |
| --- | --- |
| 카드 간 간격 | 12 |
| 카드 내부 여백 | 16 (썸네일은 예외, full-bleed) |
| 카드 모서리 | 12 |
| 이름 | 15, semibold |
| 제목 | 17, semibold, 행간 1.4 |
| 시각 / 플랫폼 | 13, regular, 낮은 명도 |
| 하단 출처 | 13, regular |

간격은 4의 배수로 통일한다. 값 자체보다 일관성이 중요하다.

### 페이지네이션

- `GET /api/v1/feed`는 커서 기반이다. `size` 기본 20, 최대 50.
- 정렬은 `publishedAt DESC, postId DESC` 안정 정렬이라 무한 스크롤에서 중복이나 누락이 없다.
  (`docs/specs/feed.md` 8장)
- `nextCursor`가 `null`이면 마지막 페이지다. 커서 값을 클라이언트가 해석하지 않는다.
- 하단에 도달하기 전에 미리 다음 페이지를 요청한다. 스크롤이 멈추는 순간을 만들지 않는다.

---

## 6. 아직 정하지 않은 것

### 아바타 자리에 무엇을 넣을지

인스타그램 카드는 아바타로 시작한다. 사람 중심 피드에서 아바타는 카드를 훑을 때 "누구 글인지" 즉시 알려주는
가장 빠른 신호다. 그런데 **우리에게는 친구 아바타 데이터가 없다.**

Blog.zip의 친구는 Blog.zip 사용자가 아니다. (PRD P-004) 프로필 이미지를 가질 주체가 없다.
현재 `GET /api/v1/feed` 응답에도 이미지 필드가 없다.

후보:

| 안 | 내용 | 비용 | 문제 |
| --- | --- | --- | --- |
| 이름 이니셜 | `friendName` 첫 글자 + 배경색 | 없음. 클라이언트만 | 같은 글자 친구가 구분되지 않음 |
| 플랫폼 아이콘 | 네이버/Velog/Tistory 마크 | 없음 | 사람이 아니라 플랫폼이 주체로 보임. P-001과 어긋남 |
| 블로그 favicon | Blog에 `faviconUrl` 저장 | Spec + 스키마 변경 | 개인 블로그는 favicon이 없거나 조악함 |
| 아바타 없음 | 이름만 크게 | 없음 | 스캔 속도가 떨어짐 |

**추천: 이름 이니셜.** 데이터 추가 없이 가능하고, 주체가 사람이라는 점(P-001)을 지킨다.
배경색은 `subscriptionId` 해시로 결정하면 같은 친구가 항상 같은 색을 갖는다.

favicon으로 가려면 `feed.md`와 `blog-subscription.md`의 데이터 항목 수정, 마이그레이션 추가가 필요하다.
지금 결정하지 않고 이니셜로 시작한 뒤 실제 화면을 보고 판단하는 것을 제안한다.

### 그 외

- 다크 모드 지원 시점
- 친구별 필터링 진입점 (`GET /api/v1/subscriptions/{id}/posts`는 이미 있다)
- 당겨서 새로고침이 실제 수집을 트리거할지, 저장된 Post만 다시 조회할지
  (수집 주기가 30분이므로 매번 수집을 돌리면 외부 요청 제한과 충돌한다)
- 읽지 않은 글만 보기 토글 (`unreadOnly` 파라미터는 이미 있다)

---

## 7. 참고 자료

- [Instagram brand - Layout](https://about.instagram.com/brand/layout) - "Simple. Flexible. Content-first.", full-bleed 원칙
- [Meta Design - The new Instagram brand identity](https://www.meta.com/design-at-meta/blog/the-new-instagram-brand-identity/) - 2026 리프레시 방향
- [Instagram brand assets and guidelines](https://www.meta.com/brand/resources/instagram/instagram-brand/) - 상표 사용 제약
- [Instagram aspect ratio guide 2026](https://growthscribe.com/aspect-ratio-for-instagram/) - 4:5가 모바일 피드 점유 최대
- [Instagram post size guide 2026](https://recurpost.com/instagram-scheduler/instagram-post-size-guide/) - 1080x1350 권장
- [Touch target size and mobile accessibility](https://buildwithaccess.com/blog/touch-target-size-mobile-accessibility-wcag) - WCAG 2.2, Apple HIG 44pt, Material 48dp
- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) - 플랫폼 가이드라인

인스타그램은 앱 UI 컴포넌트 규격을 공개하지 않는다. 5장의 수치는 위 원칙과 플랫폼 가이드라인에서
우리가 정한 값이며 인스타그램의 실제 구현값이 아니다.
