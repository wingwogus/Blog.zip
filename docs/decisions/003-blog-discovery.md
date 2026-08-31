# 003. Blog 탐색 - RSS/Atom Feed만 지원

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/blog-subscription.md`, `docs/specs/blog-ownership.md`
- 관련 Decision: `004-post-collection.md`

---

## 1. Context

`docs/specs/blog-subscription.md` FR-002는 입력 URL에서 Blog와 새 글 수집 경로를 찾으라고만 정하고, 어떤 방식까지 지원할지는 이 Decision으로 넘겼다.

선택지는 두 축이다.

- 어떤 플랫폼을 지원할지: 플랫폼별 화이트리스트 vs 방식 기준
- Feed가 없는 블로그를 HTML 파싱으로 지원할지

PRD Open Question에 "RSS가 없는 Blog 지원 여부"와 "MVP에서 지원할 Blog 플랫폼의 정확한 범위"가 있다.

---

## 2. Decision

**RSS 또는 Atom Feed를 제공하는 블로그만 지원한다. 플랫폼 화이트리스트를 두지 않는다.**

지원 판단 기준은 플랫폼 이름이 아니라 "유효한 Feed 문서를 찾을 수 있는가" 하나다. 그 결과로 Naver Blog, Velog, Tistory, RSS를 내보내는 개인 블로그가 자연히 지원 대상이 된다.

Feed가 없으면 지원하지 않는다. **HTML 파싱이나 크롤링으로 게시물 목록을 만들지 않는다.**

### 탐색 순서

1. 입력 URL을 정규화한다. (`blog-subscription.md` FR-005)
2. 알려진 플랫폼 패턴에 맞으면 해당 규칙으로 Feed 주소를 만든다.
3. 문서를 받아 `<link rel="alternate">`에서 `application/rss+xml` 또는 `application/atom+xml`을 찾는다.
4. 못 찾으면 관용 경로를 순서대로 시도한다: `/rss`, `/feed`, `/rss.xml`, `/atom.xml`, `/index.xml`
5. 찾은 주소를 실제로 파싱해 RSS 또는 Atom 문서인지 확인한다. 파싱에 성공하고 항목이 0개 이상이면 지원 가능으로 판정한다.

3번 이후 단계에서도 실패하면 `BLOG_004 BLOG_NOT_SUPPORTED`다.

### 플랫폼 패턴 (2단계에서 사용)

| platform | 입력 예 | Feed 주소 규칙 |
| --- | --- | --- |
| `NAVER` | `blog.naver.com/{id}`, `m.blog.naver.com/{id}` | `rss.blog.naver.com/{id}.xml` |
| `VELOG` | `velog.io/@{id}` | `api.velog.io/rss/@{id}` |
| `TISTORY` | `{name}.tistory.com`, 연결된 커스텀 도메인 | `{host}/rss` |
| `GENERIC` | 그 외 | 3~4단계로 탐색 |

플랫폼 패턴은 탐색을 빠르게 하기 위한 최적화다. 패턴이 실패하면 GENERIC 경로로 넘어간다. 패턴이 깨져도 서비스가 죽지 않아야 한다.

`platform` 값은 화면에 플랫폼 이름을 보여주는 용도로도 쓴다. (`blog-subscription.md` FR-003)

### 외부 요청 상한

| 항목 | 값 |
| --- | --- |
| 연결 타임아웃 | 3초 |
| 응답 타임아웃 | 5초 |
| 탐색 1건 전체 예산 | 10초 |
| 응답 본문 상한 | 2MB |
| 리다이렉트 최대 | 3회 |
| 관용 경로 시도 최대 | 5개 (위 목록) |

- 응답 본문은 상한까지만 읽고 초과하면 중단한다. `Content-Length`를 신뢰하지 않는다. 실제 읽은 바이트로 판단한다.
- 전체 예산을 넘으면 남은 후보를 시도하지 않고 `BLOG_003 BLOG_NOT_REACHABLE`로 끝낸다.
- User-Agent를 명시한다. 상대 서버가 우리를 식별할 수 있어야 한다.

### SSRF 차단

`blog-subscription.md` FR-001의 요구사항을 다음으로 구현한다.

- 허용 scheme: `http`, `https`
- DNS 해석 결과가 다음에 속하면 거부한다: 루프백, 사설(RFC 1918), 링크 로컬, 유니크 로컬(IPv6 fc00::/7), 멀티캐스트, 예약 대역, `0.0.0.0/8`
- **리다이렉트마다 재검증한다.** 최초 URL만 검사하면 우회된다.
- DNS 해석과 실제 연결 사이의 재바인딩을 막기 위해, 검증한 IP로 직접 연결한다.
- 거부는 `BLOG_002 BLOCKED_BLOG_URL`이며 요청을 보내지 않는다.

### 요청 제한

- 사용자별 탐색 요청: 분당 10회. 초과 시 `BLOG_006 TOO_MANY_LOOKUP_REQUESTS`
- 카운터는 인메모리다. (`009-ephemeral-state.md`)

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| 플랫폼 화이트리스트만 지원 | 각 플랫폼에 최적화, 예측 가능 | 개인 블로그를 못 쓴다. PRD가 "플랫폼에 종속되지 않는다"고 명시 | PRD 14장과 어긋난다 |
| Feed 없는 블로그를 HTML 파싱 지원 | 지원 범위 최대 | 사이트마다 파서가 필요하고 구조가 바뀌면 조용히 깨진다. 유지보수가 MVP 범위를 넘는다 | 지원 범위보다 유지보수 비용이 크다 |
| 사용자가 Feed 주소를 직접 입력 | 구현 단순 | PRD P-002 정면 위반 | 제품 원칙 위반 |
| 헤드리스 브라우저로 렌더링 후 파싱 | SPA 블로그도 가능 | 운영 비용과 복잡도가 급증 | MVP 범위를 크게 넘는다 |

---

## 4. Trade-off

- RSS를 끄거나 제공하지 않는 블로그는 구독할 수 없다. 사용자에게는 "아직 이 블로그는 지원하지 않습니다"로 끝난다.
  브런치, 일부 노션 기반 블로그가 여기 걸린다.
- 플랫폼 패턴은 상대 서비스가 URL 구조를 바꾸면 깨진다. 그래서 패턴 실패 시 GENERIC 탐색으로 흘러가게 만든다.
- Feed는 보통 최근 N개만 담는다. 과거 글 전체는 가져올 수 없다. 제품이 "새 글을 놓치지 않는다"에 집중하므로 문제되지 않는다.
- 타임아웃 10초는 사용자가 URL 입력 후 기다리는 시간이다. 짧으면 느린 블로그가 실패하고, 길면 사용자가 답답하다. 실사용 후 조정한다.

---

## 5. Consequences

- Feed 파서는 RSS 2.0과 Atom 1.0을 모두 처리해야 한다. 라이브러리 하나를 쓰고 직접 XML을 파싱하지 않는다.
  외부 XML을 직접 다루면 XXE와 엔티티 확장 공격 표면이 생긴다. 파서에서 외부 엔티티와 DTD를 반드시 비활성화한다.
- `BLOG_004 BLOG_NOT_SUPPORTED`의 사용자 노출 문구에 "RSS"를 쓰지 않는다. (PRD P-002)
  `blog-subscription.md` Acceptance Criteria에 이미 검증 항목이 있다.
- 탐색 로직은 외부 호출이므로 트랜잭션 밖에서 실행한다. (`007-persistence-stack.md`)
- 테스트는 실제 외부 블로그에 요청하지 않는다. 로컬 스텁 서버와 고정 Feed Fixture를 쓴다.
  최소한 다음 Fixture가 필요하다: RSS 2.0, Atom 1.0, `<link rel=alternate>`가 있는 HTML, Feed 없는 HTML, 사설 주소로 향하는 리다이렉트.

---

## 6. 재검토 조건

- 사용자가 실제로 추가하려는 블로그 중 미지원 비율이 높을 때
- 특정 플랫폼이 Feed를 중단할 때
- 브런치처럼 수요가 확인된 미지원 플랫폼이 생길 때
