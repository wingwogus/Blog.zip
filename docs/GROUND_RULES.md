# 개발 협업 가이드

## 디렉토리 구조

```text
project/
├── README.md
├── AGENTS.md
├── CLAUDE.md
├── CONTRIBUTING.md
│
├── docs/
│   ├── PRD.md
│   ├── GROUND_RULES.md
│   │
│   ├── specs/
│   │   ├── auth.md
│   │   ├── blog-subscription.md
│   │   ├── feed.md
│   │   ├── blog-ownership.md
│   │   └── ...
│   │
│   └── decisions/
│       ├── 001-database-selection.md
│       ├── 002-auth-strategy.md
│       └── ...
│
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── feature.yml
│   │   ├── task.yml
│   │   ├── bug.yml
│   │   └── config.yml
│   │
│   └── pull_request_template.md
│
├── backend/
│   ├── AGENTS.md
│   └── ...
│
└── frontend/
    ├── AGENTS.md
    └── ...
```

### 문서 역할

- `docs/PRD.md`

  - 프로젝트 전체 목표, 사용자, 핵심 기능, 범위 관리
  - 제품 수준 Source of Truth

- `docs/specs/`

  - 기능별 동작, 비즈니스 규칙, API 요구사항, 완료 조건 관리
  - 기능 수준 Source of Truth

- `docs/decisions/`

  - 장기적으로 유지되는 기술적 의사결정과 선택 이유 기록

- `AGENTS.md`

  - AI 하네스를 포함한 프로젝트 공통 개발 규칙
  - 특정 도구에 종속되지 않는 규칙 중심

- `CLAUDE.md`

  - Claude Code 사용 시 `AGENTS.md`를 참조하기 위한 진입점

---

# Source of Truth

다음 우선순위를 기준으로 개발한다.

```text
PRD
↓
Feature Spec
↓
Technical Decision
↓
GitHub Issue
↓
Implementation
```

코드와 문서가 충돌하는 경우 기존 코드를 기준으로 문서를 자동 수정하지 않는다.

먼저 어떤 동작이 올바른 요구사항인지 확인한 뒤 잘못된 쪽을 수정한다.

### PRD 수정 기준

다음 경우 `docs/PRD.md`를 수정한다.

- 프로젝트 목표 변경
- 제품 범위 변경
- 핵심 기능 추가 또는 제거
- 주요 사용자 흐름 변경

### Spec 수정 기준

다음 경우 관련 `docs/specs/*.md`를 같은 PR에서 수정한다.

- 사용자에게 보이는 기능 동작 변경
- 비즈니스 규칙 변경
- API 계약 또는 동작 변경
- 데이터의 의미 변경
- 예외 처리 정책 변경
- Acceptance Criteria 변경

다음 경우 일반적으로 Spec을 수정하지 않는다.

- 내부 리팩터링
- 함수/클래스명 변경
- 패키지 이동
- 단순 성능 최적화
- 구현 세부사항 변경

### Decision 작성 기준

다음과 같이 향후 다른 개발자도 같은 고민을 할 가능성이 높은 결정은 `docs/decisions/`에 남긴다.

- DB 선택
- 인증 방식
- 메시징 방식
- 캐싱 전략
- Retry / Rate Limit 전략
- 데이터 일관성 전략
- 시스템 간 통신 구조

일회성 구현 판단이나 쉽게 변경 가능한 코드 구조는 별도 Decision으로 남기지 않는다.

---

# Prefix

| Prefix     | 사용 기준                |
| ---------- | -------------------- |
| `feat`     | 새로운 기능 추가            |
| `fix`      | 버그 수정                |
| `docs`     | 문서 수정                |
| `refactor` | 외부 동작 변경 없는 코드 리팩터링  |
| `test`     | 테스트 추가 또는 수정         |
| `chore`    | 설정, 의존성, 기타 유지보수 작업  |
| `ci`       | CI/CD, 빌드 및 배포 설정 변경 |

---

# 브랜치 전략

```text
main
└── 실제 배포 기준 브랜치

dev
└── 개발 통합 브랜치
```

일반적인 개발 흐름:

```text
Issue
↓
개별 Branch
↓
PR → dev
↓
통합 테스트
↓
dev → main
```

`main` 직접 Push는 금지한다.

`dev → main` Merge는 릴리즈 또는 프로젝트 마감 시점에 진행한다.

CI/CD 트리거 대상은 프로젝트 운영 상황에 맞게 `dev` 또는 `main`으로 설정한다.

---

# 브랜치 이름

```text
prefix/#이슈번호-description-with-dash
```

예:

```text
feat/#21-blog-subscription
fix/#32-duplicate-post
refactor/#45-auth-service
docs/#51-update-auth-spec
```

Branch는 가능하면 하나의 Issue만 담당한다.

관련 없는 작업을 하나의 Branch에 섞지 않는다.

---

# Issue 운영 방식

초기에는 팀 전체가 Feature 수준의 큰 Issue를 생성하고 담당자를 정한다.

예:

```text
#10 사용자 인증
#11 블로그 구독
#12 Feed
#13 내 블로그 연결
```

Feature 담당자는 관련 Spec을 읽고 실제 개발 가능한 단위의 Sub-Issue로 분리한다.

예:

```text
#11 블로그 구독
├── #21 블로그 URL 검증
├── #22 Feed 자동 탐색
├── #23 Subscription 생성
└── #24 중복 구독 방지
```

### Issue 크기 기준

가능하면 다음 기준을 따른다.

```text
Issue 하나 ≒ PR 하나
```

너무 세부적인 클래스/메서드 수준으로 Issue를 나누지 않는다.

---

# Issue 제목

```text
prefix: 한국어 설명
```

예:

```text
feat: 친구 블로그 구독 기능
fix: 중복 Post 저장 문제 수정
```

---

# Feature Issue Template

```markdown
## ✨ 기능 설명

<!-- 어떤 문제를 해결하고 어떤 기능을 제공하는지 작성 -->

## 📐 범위

- [ ] 작업 내용 1
- [ ] 작업 내용 2
- [ ] 작업 내용 3

## ✅ 완료 조건

- [ ] 기능 동작 확인
- [ ] 테스트 완료
- [ ] 필요한 문서 반영

## 🔗 참고

- 화면
- 기획
- 레퍼런스
```

---

# Task Issue Template

```markdown
## 🔗 Parent Issue

- #

## 📄 관련 Spec

- `docs/specs/...`

## 🛠 작업 내용

<!-- 해당 Issue에서 구현할 범위 -->

- [ ] 작업 1
- [ ] 작업 2

## ✅ Done

- [ ] 구현 완료
- [ ] 테스트 완료
```

---

# Bug Issue Template

```markdown
## 🐛 문제

<!-- 발생한 문제 설명 -->

## 📄 관련 Spec

- `docs/specs/...`

## 기대 동작

<!-- Spec상 정상 동작 -->

## 실제 동작

<!-- 현재 발생하는 문제 -->

## 재현 방법

1.
2.
3.

## 완료 조건

- [ ] 문제 수정
- [ ] 기존 기능 영향 확인
- [ ] 가능하면 Regression Test 추가
```

Bug는 기존 Spec이 올바르고 구현이 잘못된 경우 Spec을 수정하지 않는다.

---

# 커밋 메시지

기본 형식:

```text
prefix(domain): 한국어 설명
```

예:

```text
feat(auth): 로그인 API 추가
fix(feed): 중복 Post 저장 방지
refactor(auth): 토큰 발급 로직 분리
docs(spec): 블로그 구독 요구사항 수정
```

### Commit Subject

- 어떤 변경인지 명확하게 작성
- 너무 길게 작성하지 않음
- 어떻게 구현했는지보다 무엇이 바뀌었는지를 표현

### Atomic Commit

- 커밋 하나에는 하나의 논리적 변경만 포함한다.
- 기능 구현, 무관한 리팩터링, 설정 변경, 문서 변경을 한 커밋에 섞지 않는다.
- 기능 또는 버그 수정의 테스트는 해당 구현 커밋에 함께 포함한다.
- 설정이나 문서 변경은 그 구현에 반드시 필요한 경우에만 같은 커밋에 포함한다.
- 커밋 전 `git diff --cached --check`와 `git diff --cached --stat`으로 staged 변경이 하나의 의도인지 확인한다.
- 원자성이 불분명하면 커밋을 나눈다. 커밋을 나누기 위해 동작하지 않는 중간 상태를 만들지는 않는다.

### Commit Body

필요한 경우 본문에 변경 이유를 작성한다.

```text
fix(feed): 외부 Feed 호출 동시성 제한

병렬 요청 증가 시 외부 플랫폼 Rate Limit을 초과해
간헐적으로 429 응답이 발생했다.

동시 요청 수를 제한해 반복 실패를 방지한다.
```

다음과 같은 경우 Commit Body에 이유를 남기는 것을 권장한다.

- 코드만 보면 이유를 알기 어려운 변경
- 특정 제약 때문에 선택한 구현
- 향후 다시 잘못 수정될 가능성이 있는 코드

장기적으로 유지할 필요가 있는 기술적 결정은 Commit에만 남기지 않고 `docs/decisions/`에도 기록한다.

---

# Pull Request 제목

```text
prefix: 한국어 설명
```

예:

```text
feat: 친구 블로그 구독 기능
fix: 중복 Post 저장 문제 수정
```

---

# Pull Request Template

PR 본문 형식은 `.github/pull_request_template.md`를 기준으로 한다.

포함해야 하는 항목:

```text
요약
관련 이슈
관련 문서 (PRD / Spec / Decision)
변경 사항 분류
작업 내용
문서 영향
테스트
AI 사용 여부
```

---

# PR Review 기준

모든 PR은 최소 1명 이상의 팀원 리뷰 후 Merge한다.

PR 작성자는 Merge 전 다음 사항을 확인한다.

- 관련 Issue 연결
- 관련 Spec 확인
- 필요한 문서 수정
- 테스트 완료
- AI가 생성한 코드 직접 검토
- 미해결 Blocking Comment 없음

### 리뷰 Comment 구분

필요하면 다음 Prefix를 사용한다.

```text
blocking:
반드시 수정해야 하는 내용

suggestion:
선택적으로 개선할 수 있는 내용

question:
구현 의도 확인

nit:
사소한 스타일 또는 표현 의견
```

---

# AI Review Bot

PR 생성 후 AI Review Bot의 피드백을 확인한다.

사용 도구 예:

- CodeRabbit
- Gemini Code Assist

모든 AI 피드백을 무조건 반영하지 않는다.

다음 기준으로 작성자가 직접 판단한다.

```text
AI 의견
↓
실제 문제인가?
↓
프로젝트 Spec / 설계와 일치하는가?
↓
필요한 경우 반영
```

AI 리뷰와 사람 리뷰가 충돌하는 경우 팀원의 판단을 우선한다.

AI 리뷰에서 중요한 문제를 발견한 경우 수정 후 해당 Conversation을 Resolve한다.

---

# AI 개발 도구 사용

팀원은 각자 원하는 AI 개발 도구를 사용할 수 있다.

예:

- OMO
- Claude Code
- Codex
- Cursor
- Gemini

특정 하네스에서 생성한 Plan이나 Memory는 팀의 Source of Truth가 아니다.

모든 AI 도구는 다음 공통 문서를 기준으로 작업한다.

```text
docs/PRD.md
docs/specs/
docs/decisions/
AGENTS.md
```

AI가 Spec과 다른 구현을 제안한 경우 바로 구현하지 않고 요구사항 변경 여부를 먼저 확인한다.

---

# 작업 전체 흐름

```text
PRD
↓
Feature Spec
↓
Feature Issue
↓
담당자 배정
↓
필요하면 Sub-Issue 생성
↓
Branch 생성
↓
개발
↓
Test
↓
PR
↓
PRD / Spec / Decision 영향 확인
↓
AI Review + Team Review
↓
dev Merge
↓
릴리즈 시 main Merge
```

핵심 원칙은 다음과 같다.

```text
PRD       = 무엇을 만드는가
Spec      = 기능이 어떻게 동작해야 하는가
Decision  = 왜 이렇게 설계했는가
Issue     = 무엇을 작업하는가
PR        = 실제로 무엇을 변경했는가
Code      = 현재 구현
```
