# frontend/AGENTS.md

Blog.zip 웹 클라이언트 규칙이다. 루트 `AGENTS.md`를 먼저 따르고, 여기 규칙이 충돌하면 이 문서를 우선한다.

## 기술 기준

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 프레임워크 | React 19 | `docs/decisions/012-frontend-stack.md` |
| 빌드 | Vite 8 | 012 |
| 언어 | TypeScript 5.9 (strict) | 012 |
| 라우팅 | React Router 7 | 012 |
| 서버 데이터 | TanStack Query 5 | 012 |
| 스타일 | Tailwind CSS 4 | 012 |
| 테스트 | Vitest + Testing Library | 012 |
| HTTP | `fetch` 래퍼 (`src/api/client.ts`) | 012 |
| 패키지 매니저 | npm | 012 |

상태 관리 라이브러리를 넣지 않는다. 서버 데이터는 Query 캐시, accessToken은 모듈 변수다.

## 실행

```bash
cd frontend
npm install
npm run dev     # http://localhost:3000, /api는 :8080으로 프록시
npm run build   # tsc -b && vite build
npm test        # vitest run
```

백엔드가 함께 떠 있어야 데이터가 보인다. `backend/AGENTS.md` 참고.

## 디렉토리

```text
src/
├── api/        클라이언트, 응답 타입, 에러 코드
├── features/   기능별 화면과 컴포넌트 (feed, auth, subscription, ownership)
├── lib/        순수 함수 유틸
├── components/ 기능에 속하지 않는 공용 UI
└── test/       테스트 설정
```

기능 우선, 그다음 역할. 백엔드와 같은 방향이다.

## API 호출 규칙

`src/api/client.ts`만 `fetch`를 호출한다. 컴포넌트에서 직접 호출하지 않는다.

클라이언트가 이미 처리하므로 화면에서 다시 하지 않을 것:

- `{success, data, error}` 래퍼 해제
- 401 자동 재발급과 원요청 1회 재시도
- 재발급 요청 단일화

분기는 `error.code`로만 한다. 서버가 주는 `message` 문자열로 분기하거나 테스트로 고정하지 않는다.
(`docs/decisions/010-api-response-contract.md`)

## UX 원칙 (PRD 7장)

- People First: 화면의 주체는 블로그 URL이 아니라 사람이다. 피드 항목은 사용자가 지정한 친구 이름을 우선 보여준다. (P-001)
- RSS Abstraction: "RSS", "Atom", "Feed URL" 같은 용어를 사용자 화면에 노출하지 않는다. 사용자는 일반 블로그 URL만 입력한다. (P-002)
- Original Content First: 모든 Post는 원본 Blog와 원문 링크를 확인할 수 있어야 한다. 본문을 원문 대체 수준으로 재구성하지 않는다. (P-006)
- 낮은 진입장벽: 친구 초대나 추가 온보딩 없이 가입 직후 블로그를 추가할 수 있어야 한다. (P-005)
- Ownership 미인증 블로그에 공식 운영자 배지 등 소유를 암시하는 표현을 쓰지 않는다. (BR-008)

## Feed 디자인

Feed는 **카드 스크롤**이다. 세로로 내리면서 친구들의 새 글을 발견하고 원문으로 이동한다.
인앱 열람(웹뷰, 리더 뷰)은 채택하지 않았다.

레이아웃, 상태 표현, 수치는 `frontend/DESIGN.md`를 참고한다.
기능 동작과 완료 조건은 `docs/specs/feed.md`가 Source of Truth다.

자주 어기는 것:

- 카드에 본문이나 발췌를 표시하지 않는다. 저장하지 않는 데이터다.
- 썸네일이 없는 카드가 정상적으로 자주 나온다. 플레이스홀더를 넣지 않고 제목 중심 카드로 좁힌다.
- 좋아요, 댓글, 공유 액션 바를 만들지 않는다. PRD 5장 Non-Goals.
- 원문에서 돌아왔을 때 스크롤 위치를 유지한다. 이게 카드 스크롤의 핵심 요구사항이다.
- 인스타그램의 그라디언트, 폰트, 로고를 쓰지 않는다. Meta 상표다.

## 화면 흐름

`docs/PRD.md` 10장 흐름을 기준으로 한다.

- 친구 블로그 추가: URL 입력 → 블로그 정보 확인 → "누구의 블로그인가요?" → 이름 입력 → 피드 추가
- 친구 글 확인: 카드 피드 스크롤 → 카드 선택 → 인앱 브라우저로 원문 → 닫으면 스크롤 위치 복원
- 내 블로그 연결: Profile → URL 입력 → 소유권 인증 → 연결 완료

각 화면의 상세 동작과 예외 표시는 해당 `docs/specs/*.md`를 따른다.

## 구현 규칙

- 로딩, 빈 상태, 수집 실패 상태를 화면마다 명시적으로 다룬다. 무한 스피너로 두지 않는다.
- 외부 링크 이동은 원문 출처를 사용자가 알 수 있게 표시한다.
- 접근성: 시맨틱 요소와 키보드 포커스를 유지한다. 이미지에는 대체 텍스트를 넣는다.
- 사용자 입력 URL을 그대로 신뢰해 렌더링하지 않는다.
