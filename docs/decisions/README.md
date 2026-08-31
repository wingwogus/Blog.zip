# Technical Decisions

장기적으로 유지되는 기술적 의사결정과 선택 이유를 기록하는 디렉토리다.

각 Decision은 다음 내용을 정의한다.

```text
기술적으로 어떤 방식을 선택했는가
왜 해당 방식을 선택했는가
어떤 대안을 검토했는가
어떤 Trade-off가 존재하는가
```

## 파일 규칙

- 파일명: `NNN-short-title.md` (예: `001-database-selection.md`)
- 번호는 3자리 증가 번호를 사용하며 재사용하지 않는다.
- 새 Decision은 `TEMPLATE.md`를 복사해 작성한다.
- 결정이 뒤집히면 기존 문서를 삭제하지 않고 `Superseded by NNN`으로 표시한다.

## 작성 대상

- DB 선택
- 인증 방식
- 메시징 방식
- 캐싱 전략
- Retry / Rate Limit 전략
- 데이터 일관성 전략
- 시스템 간 통신 구조

일회성 구현 판단이나 쉽게 되돌릴 수 있는 코드 구조는 Decision으로 남기지 않는다.

## 목록

| ID | 제목 | 상태 | 참조 Spec |
| --- | --- | --- | --- |
| 001 | Database 선택 - PostgreSQL | Accepted | 전체 |
| 002 | 인증 전략 - JWT + 자동 재발급 | Accepted | `auth.md` |
| 003 | Blog 탐색 - RSS/Atom만 지원 | Accepted | `blog-subscription.md` |
| 004 | Post 수집 - 주기·실패·저장 범위 | Accepted | `feed.md` |
| 005 | Ownership Verification | 미작성 | `blog-ownership.md` |
| 006 | 식별자 전략 - ULID | Accepted | 전체 |
| 007 | Persistence Stack - Kotlin + JPA + QueryDSL | Accepted | 전체 |
| 008 | Schema Migration - Flyway | Accepted | 전체 |
| 009 | 단기 상태 저장 - 인메모리 | Accepted | `auth.md`, `blog-subscription.md`, `feed.md`, `blog-ownership.md` |
| 010 | API 생답 및 에러 코드 규약 | Accepted | 전체 |
| 011 | 백엔드 모듈 구조 - 단일 모듈 | Accepted | - |
| 012 | 프론트엔드 스택 - React + Vite | Accepted | `feed.md`, `auth.md` |

번호는 Spec에서 이미 참조하고 있으므로 임의로 재배치하지 않는다.

미작성 005는 해당 기능 구현을 시작하기 전에 작성한다.
