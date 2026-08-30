# Contributing

전체 규칙은 `docs/GROUND_RULES.md`에 있다. 이 문서는 실제 작업 순서만 요약한다.

## 작업 흐름

```text
Spec 확인
↓
Issue 생성 / 담당
↓
Branch 생성
↓
개발 + 테스트
↓
PR → dev
↓
AI Review + Team Review
↓
dev Merge
```

## 1. Issue

- 제목: `prefix: 한국어 설명`
- Feature Issue는 큰 단위로 만들고, 담당자가 Spec을 읽은 뒤 Task Sub-Issue로 쪼갠다.
- `Issue 하나 ≒ PR 하나`를 목표로 한다.

## 2. Branch

```text
prefix/#이슈번호-description-with-dash
```

예: `feat/#21-blog-subscription`

`main`에 직접 Push하지 않는다. PR 대상은 `dev`다.

## 3. Commit

```text
prefix(domain): 한국어 설명
```

코드만 봐서는 이유를 알기 어려운 변경은 Body에 배경을 남긴다.
오래 유지될 기술 결정은 `docs/decisions/`에도 기록한다.

## 4. 문서

- 사용자에게 보이는 동작 / 비즈니스 규칙 / API 계약 / 데이터 의미 / 예외 정책 / 완료 조건 변경
  → 같은 PR에서 `docs/specs/*.md` 수정
- 제품 목표 / 범위 / 핵심 기능 / 주요 흐름 변경
  → `docs/PRD.md` 수정
- 내부 리팩터링, 이름 변경, 패키지 이동
  → 문서 수정 없음

## 5. PR

- 템플릿(`.github/pull_request_template.md`)의 모든 항목을 채운다.
- 관련 Issue를 `Closes #번호`로 연결한다.
- 최소 1명 리뷰 후 Merge한다.
- 리뷰 Comment Prefix: `blocking:`, `suggestion:`, `question:`, `nit:`
- Blocking Comment가 남아 있으면 Merge하지 않는다.

## 6. 테스트

- 변경한 모듈의 테스트를 실행하고 결과를 PR에 적는다.
- 새 기능과 버그 수정에는 테스트를 추가한다.

## 7. AI 도구

원하는 도구를 자유롭게 사용한다. 단,

- AI가 생성한 코드는 직접 검토하고 이해한 뒤 PR을 만든다.
- PR의 AI 사용 항목을 체크한다.
- AI가 Spec과 다른 구현을 제안하면 요구사항 변경 여부를 먼저 확인한다.
