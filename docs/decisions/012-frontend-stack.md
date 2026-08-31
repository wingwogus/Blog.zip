# 012. 프론트엔드 스택 - React + Vite

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/feed.md`, `docs/specs/auth.md`
- 관련 Decision: `002-auth-strategy.md`, `010-api-response-contract.md`
- 관련 문서: `frontend/DESIGN.md`

---

## 1. Context

MVP는 웹으로 먼저 테스트한다. Native Mobile Application은 PRD 12장에서 제외 범위다.

React를 쓰기로 정해졌고, 나머지 선택이 남았다. 이 제품의 프론트엔드 특성:

- 화면 수가 적다. Feed, 친구 추가, 친구 목록, 내 블로그, 로그인 정도.
- **서버 데이터가 거의 전부다.** 클라이언트 고유 상태가 사실상 없다.
- Feed는 커서 기반 무한 스크롤이다. (`docs/specs/feed.md`)
- 인증은 accessToken 자동 재발급이 필요하다. (`docs/decisions/002-auth-strategy.md`)

---

## 2. Decision

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| 빌드 | Vite 8 | 설정이 적고 dev 서버가 빠르다 |
| 언어 | TypeScript 7 | API 응답 구조가 Spec에 고정돼 있어 타입이 실질적 검증이 된다 |
| 라우팅 | React Router 7 | 화면 수가 적어 프레임워크급(Next.js)이 필요 없다 |
| 서버 데이터 | TanStack Query 5 | 커서 무한 스크롤, 캐시, 재요청이 이 라이브러리의 핵심 기능이다 |
| 스타일 | Tailwind CSS 4 | 카드 레이아웃에 클래스만으로 충분하고 CSS 파일이 늘지 않는다 |
| 테스트 | Vitest + Testing Library + MSW | Vite와 설정을 공유한다. MSW로 네트워크 경계에서 목을 만든다 |
| HTTP | `fetch` (래퍼 직접 작성) | 응답 래퍼 해제와 자동 재발급 로직이 필요해 어차피 래퍼를 만든다. axios를 추가할 이유가 없다 |
| 패키지 매니저 | npm | 워크스테이션에 기본 설치돼 있고 CI에서 별도 설치가 필요 없다 |

### 상태 관리 라이브러리를 넣지 않는다

Redux, Zustand, Jotai 모두 넣지 않는다.

이 앱의 상태는 대부분 서버 데이터이고 그건 TanStack Query가 캐시로 관리한다.
남는 클라이언트 상태는 accessToken 하나이며, 그건 모듈 스코프 변수로 충분하다. (아래)

실제로 필요해지면 그때 Zustand를 넣는다. YAGNI.

### Next.js를 쓰지 않는다

- SSR과 SEO가 필요 없다. 로그인해야 보이는 개인 피드다.
- 백엔드가 이미 Spring이다. Next.js의 서버 기능은 중복이다.
- Vite + React Router가 이 규모에 맞다.

### accessToken은 메모리에 둔다

`docs/decisions/002-auth-strategy.md`가 요구하는 것이다. localStorage에 두지 않는다. XSS 하나로 탈취된다.

모듈 스코프 변수에 담고, 새로고침으로 사라지면 재발급으로 복구한다.
refreshToken은 `HttpOnly` 쿠키이므로 JS가 읽지 않는다. `fetch`에 `credentials: "include"`를 항상 붙인다.

### API 클라이언트가 반드시 처리할 것

`docs/decisions/002-auth-strategy.md`와 `010-api-response-contract.md`에서 넘어온 요구사항이다.
이건 컴포넌트에 흩어지면 안 되고 클라이언트 한 곳에서 끝나야 한다.

1. **응답 래퍼 해제.** `{success, data, error}`에서 `data`를 꺼내 반환한다.
   컴포넌트가 `res.data.data`를 다루지 않게 한다.
2. **`error.code`로만 분기.** `message` 문자열로 분기하지 않는다. 문구는 예고 없이 바뀐다.
3. **401 자동 재발급 + 원요청 1회 재시도.** 사용자가 만료를 인지하지 않는다.
4. **재발급 요청 단일화(single-flight).** 동시에 여러 요청이 401을 받아도 재발급은 한 번만 호출한다.
   그러지 않으면 회전된 refreshToken이 서로를 무효화한다.
5. 재시도는 1회만. 재발급 후에도 401이면 로그인으로 보낸다. 무한 루프를 만들지 않는다.
6. 재발급 엔드포인트 자체의 401에는 재시도하지 않는다.

4번과 5번이 실제로 자주 깨지는 지점이므로 테스트로 고정한다.

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| Next.js | 라우팅/SSR 내장, 생태계 | SSR 불필요, Spring과 서버 역할 중복, 학습량 증가 | 이 제품에 이점이 없다 |
| CRA | 익숙함 | 유지보수 중단 상태 | 새 프로젝트에서 쓸 이유가 없다 |
| SWR | 가볍다 | 커서 무한 스크롤 지원이 TanStack Query보다 얕다 | Feed가 무한 스크롤이다 |
| 직접 fetch + useState | 의존성 없음 | 캐시, 중복 요청 제거, 무한 스크롤을 직접 구현 | 재발명이다 |
| CSS Modules | 표준에 가깝다 | 카드 레이아웃 스타일을 파일로 분리하는 비용 | Tailwind가 이 규모에 빠르다 |
| styled-components | 동적 스타일에 강함 | 런타임 비용, Tailwind와 목적 중복 | 동적 스타일 요구가 없다 |
| axios | 인터셉터 편의 | 래퍼를 어차피 직접 만든다. 의존성만 늘어난다 | `fetch`로 충분하다 |

---

## 4. Trade-off

- Tailwind 클래스가 JSX를 길게 만든다. 반복되는 조합은 컴포넌트로 뽑아 완화한다.
- SSR이 없으므로 첫 로딩에 빈 화면이 잠깐 보인다. 로그인 후 쓰는 앱이라 수용한다.
- 상태 관리 라이브러리가 없어서, 전역 클라이언트 상태가 늘어나면 그때 도입 판단이 필요하다.
- `fetch` 래퍼를 직접 만들므로 재시도·재발급 로직의 버그가 우리 책임이다. 그래서 테스트를 붙인다.

---

## 5. Consequences

### 구조

```text
frontend/src/
├── api/          클라이언트, 타입, 에러 코드
├── features/     기능별 화면과 컴포넌트 (feed, auth, subscription, ownership)
├── lib/          순수 함수 유틸
└── components/   기능에 속하지 않는 공용 UI
```

백엔드와 같은 방향이다. 기능 우선, 그다음 역할. (`011-backend-module-structure.md`)

### 검증

- 순수 함수(상대 시간, 아바타 색)는 단위 테스트.
- API 클라이언트의 재발급·재시도는 테스트로 고정한다. 위 2장 4~6번.
- 컴포넌트는 Testing Library로 사용자가 보는 것을 검증한다. 구현 세부를 검증하지 않는다.
- **문구를 테스트로 고정하지 않는다.** 서버 `message`는 바뀔 수 있다. `code`와 동작만 검증한다.
  (`010-api-response-contract.md`)
- 비동기 테스트에 고정 sleep을 쓰지 않는다. Testing Library의 `waitFor`나 이벤트를 기다린다.

---

## 6. 재검토 조건

- Native App을 만들 때 (React Native 또는 별도 클라이언트)
- SEO가 필요한 공개 페이지가 생길 때 (Discovery, Phase 4)
- 전역 클라이언트 상태가 늘어 Query 캐시로 부족해질 때
