# Blog.zip

**친구가 어디에서 블로그를 쓰든, 한곳에서 계속 만날 수 있도록 한다.**

Blog.zip은 네이버 블로그, Velog, Tistory, 개인 블로그 등 서로 다른 플랫폼에 흩어진 친구들의 글을
하나의 피드에서 구독하고 볼 수 있게 하는 소셜 블로그 피드 서비스다.

사용자는 RSS나 Atom 같은 개념을 알 필요 없이 블로그 URL을 입력하고
"누구의 블로그인가요?"에 답하면 친구가 피드에 추가된다.

## 문서

| 문서 | 역할 |
| --- | --- |
| `docs/PRD.md` | 제품 수준 Source of Truth (무엇을 왜 만드는가) |
| `docs/specs/` | 기능 수준 Source of Truth (기능이 어떻게 동작하는가) |
| `docs/decisions/` | 기술적 의사결정 기록 (왜 이렇게 설계했는가) |
| `docs/GROUND_RULES.md` | 개발 협업 가이드 (브랜치, Issue, PR, 커밋 규칙) |
| `AGENTS.md` | AI 도구를 포함한 공통 개발 규칙 |
| `CONTRIBUTING.md` | 기여 절차 요약 |

작업 우선순위:

```text
PRD → Feature Spec → Technical Decision → GitHub Issue → Implementation
```

## 프로젝트 구조

```text
docs/        제품/기능/기술 문서
.github/     Issue, PR 템플릿
backend/     서버 애플리케이션
frontend/    웹 클라이언트
```

## 현재 상태

v0.1 MVP 준비 단계. PRD 확정, Feature Spec 작성 진행 예정.
기술 스택 및 실행 방법은 각 모듈 구현 시 `backend/README.md`, `frontend/README.md`에 추가한다.
