# Feature Spec

기능 수준 Source of Truth를 관리하는 디렉토리다.

각 Spec은 다음 내용을 정의한다.

```text
각 기능이 어떻게 동작해야 하는가
어떤 비즈니스 규칙을 따라야 하는가
어떤 예외 상황을 처리해야 하는가
어떤 조건을 만족하면 완료된 것인가
```

## 파일 규칙

- 파일명은 기능 단위 kebab-case를 사용한다. (예: `blog-subscription.md`)
- 새 Spec은 `TEMPLATE.md`를 복사해 작성한다.
- 하나의 파일은 하나의 기능 도메인을 담당한다.

## MVP Spec 계획

`docs/PRD.md` 11장 MVP 범위를 기준으로 다음 Spec을 작성한다.

| Spec 파일 | 범위 | 상태 |
| --- | --- | --- |
| `auth.md` | 회원가입, 로그인, 로그아웃 | 미작성 |
| `blog-subscription.md` | 블로그 URL 입력, Feed 탐색, Subscription 생성/삭제 | 미작성 |
| `feed.md` | Post 수집, 피드 조회, 원문 이동 | 미작성 |
| `blog-ownership.md` | 내 블로그 등록, 소유권 인증, Ownership 해제 | 미작성 |
| `subscription-management.md` | 친구 목록 조회, 이름 수정, 구독 해제 | 미작성 |

Spec 작성 순서와 실제 파일 구성은 작업 시점에 조정할 수 있다.

## 참고

- 제품 수준 요구사항: `docs/PRD.md`
- 협업 규칙: `docs/GROUND_RULES.md`
- 기술 의사결정: `docs/decisions/`
