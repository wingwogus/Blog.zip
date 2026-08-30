# backend/AGENTS.md

Blog.zip 서버 애플리케이션 규칙이다. 루트 `AGENTS.md`를 먼저 따르고, 여기 규칙이 충돌하면 이 문서를 우선한다.

## 현재 상태

기술 스택 미확정. 아래 항목은 첫 구현 PR에서 확정하고 이 문서와 `docs/decisions/`에 반영한다.

- [ ] 언어 / 프레임워크
- [ ] 빌드 및 테스트 명령
- [ ] 데이터베이스 (`docs/decisions/001-database-selection.md`)
- [ ] 인증 방식 (`docs/decisions/002-auth-strategy.md`)
- [ ] Feed 수집 스케줄링 및 Rate Limit 전략
- [ ] 패키지 구조 규칙

## 도메인 경계

핵심 개념은 `docs/PRD.md` 8장을 따른다. 구현에서 다음 경계를 섞지 않는다.

- `User` - 가입 사용자
- `Blog` - 외부 블로그 (Blog.zip 가입 여부와 무관하게 존재)
- `Subscription` - User와 Blog 사이의 개인 구독 관계. 구독자가 지정한 이름을 포함한다.
- `Ownership` - 인증된 소유 관계. Subscription과 별도 모델로 다룬다.
- `Post` - 외부 Blog에서 수집한 게시물

주의할 점:

- Subscription의 친구 이름은 구독자별 값이다. Blog에 저장하지 않는다. (BR-004, BR-005)
- Ownership 없이 Blog에 운영자 User를 연결하지 않는다. (BR-008)
- Blog 소유자가 나중에 Ownership을 인증해도 기존 Subscription을 변경하지 않는다. (BR-007)

## 외부 Feed 처리

- 블로그 URL과 Feed 응답은 신뢰할 수 없는 입력으로 다룬다. 파싱 전 검증한다.
- 수집 실패는 삼키지 않고 상태로 남긴다. (Should Have: Feed 수집 실패 상태 관리)
- 외부 플랫폼 호출은 동시성과 재시도 정책을 명시적으로 제한한다.

## 테스트

- 외부 네트워크에 의존하는 테스트는 고정 Fixture 또는 로컬 스텁을 사용한다.
- 고정 sleep으로 비동기 결과를 기다리지 않는다.
- 실행한 테스트 명령과 결과를 PR에 남긴다.
