# 008. Schema Migration - Flyway

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/` 전체 (각 Spec 7장 데이터)
- 관련 Decision: `001-database-selection.md`, `006-id-strategy.md`, `007-persistence-stack.md`

---

## 1. Context

스키마는 여러 사람이 각자 브랜치에서 바꾼다. 엔티티 변경만으로 스키마를 맞추면 다음이 생긴다.

- 로컬과 운영 스키마가 조용히 달라진다.
- 어떤 순서로 어떤 변경이 있었는지 기록이 남지 않는다.
- 배포 시점에 스키마 변경을 되돌릴 방법이 없다.

이 프로젝트의 유일 제약과 부분 인덱스는 비즈니스 규칙 그 자체다. (`001-database-selection.md` 5장) Hibernate가 자동 생성한 DDL로는 부분 유일 인덱스 같은 것을 정확히 표현할 수 없다.

---

## 2. Decision

**Flyway로 스키마를 관리한다. 스키마의 Source of Truth는 마이그레이션 SQL이다.**

### 설정

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

- `ddl-auto`는 모든 환경에서 `validate`다. `update`, `create`, `create-drop`을 쓰지 않는다.
  운영뿐 아니라 로컬과 테스트도 같다. 로컬만 `update`면 마이그레이션 누락을 로컬에서 못 잡는다.
- `validate`는 엔티티와 실제 스키마가 어긋나면 애플리케이션 기동을 실패시킨다. 이게 목적이다.
- `open-in-view: false`. 뷰 렌더링 시점의 지연 로딩과 트랜잭션 밖 쿼리를 막는다.

### 파일 규칙

```text
backend/src/main/resources/db/migration/
├── V1__create_users.sql
├── V2__create_blogs.sql
├── V3__create_subscriptions.sql
└── ...
```

- 형식: `V{번호}__{snake_case_설명}.sql`
- 번호는 정수 증가. 재사용하지 않는다.
- 파일 하나는 하나의 논리적 변경을 담는다.
- **머지된 마이그레이션 파일은 절대 수정하지 않는다.** 잘못됐으면 새 파일로 고친다.
  Flyway는 체크섬을 검증하므로 수정하면 다른 사람 환경에서 기동이 실패한다.

### 번호 충돌

여러 브랜치가 같은 번호를 쓰는 일이 생긴다.

- PR 리뷰에서 번호 충돌을 확인한다.
- 충돌 시 나중에 머지되는 쪽이 번호를 올린다. 머지된 쪽을 고치지 않는다.
- 아직 머지되지 않은 브랜치의 파일 번호 변경은 자유롭다.

### 되돌리기

- `undo` 마이그레이션을 쓰지 않는다. (Flyway 무료 버전에 없다)
- 되돌릴 필요가 있으면 되돌리는 마이그레이션을 새로 쓴다.
- 컬럼 삭제와 이름 변경은 두 단계로 나눈다. 배포와 스키마 변경이 동시에 안 맞는 순간을 견디게 한다.
  1. 새 컬럼 추가 + 양쪽 쓰기
  2. 다음 배포에서 옛 컬럼 제거

### 테스트

- 통합 테스트는 Testcontainers Postgres에 Flyway 마이그레이션을 실행해 스키마를 만든다.
  Hibernate 자동 생성 스키마로 테스트하지 않는다. 운영과 같은 스키마에서 검증한다.
- 마이그레이션 자체가 실행 가능한지는 테스트 기동으로 매번 검증된다.

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| Hibernate `ddl-auto: update` | 설정 없음, 빠른 초기 개발 | 변경 이력 없음, 파괴적 변경 예측 불가, 부분 인덱스 표현 불가 | 이 프로젝트의 제약이 비즈니스 규칙이다 |
| Liquibase | XML/YAML 추상화, DB 독립성, rollback 지원 | 추상화 계층이 두껍고 생성 SQL이 안 보임 | Postgres 하나만 쓰므로 DB 독립성이 필요 없다. SQL을 직접 보는 게 낫다 |
| 수동 SQL 실행 | 완전 통제 | 적용 여부 추적 불가 | 여러 사람이 작업한다 |

---

## 4. Trade-off

- 컬럼 하나 추가에도 SQL 파일을 만들어야 한다. 초기 개발 속도가 `ddl-auto: update`보다 느리다.
- 엔티티와 SQL을 둘 다 고쳐야 하므로 한쪽을 잊으면 `validate`가 기동을 막는다.
  느리지만 조용한 불일치보다 낫다.
- Postgres에 종속된 SQL을 쓴다. DB를 바꾸면 마이그레이션을 다시 써야 한다.
  Postgres만 쓰기로 했으므로 감수한다. (`001-database-selection.md`)

---

## 5. Consequences

### 초기 스키마에 반드시 들어가야 하는 제약

각 Spec의 7장에서 유일성으로 정의한 것은 DB 제약으로 만든다. 애플리케이션 검증만으로 두지 않는다.

| 제약 | 근거 |
| --- | --- |
| `subscriptions (user_id, blog_id)` UNIQUE | `blog-subscription.md` SUB-BR-007 |
| `posts (blog_id, external_id)` UNIQUE | `feed.md` FEED-BR-004 |
| `post_read_states (user_id, post_id)` UNIQUE | `feed.md` 7장 |
| `ownerships (blog_id)` UNIQUE | `blog-ownership.md` OWN-BR-006 |
| `ownerships (user_id)` UNIQUE | `blog-ownership.md` OWN-BR-007 (MVP) |
| `blogs (canonical_url)` UNIQUE | `blog-subscription.md` FR-005 |
| `users (email)` UNIQUE | `auth.md` AUTH-BR-001 |
| `ownership_verifications (user_id, blog_id) WHERE status = 'PENDING'` 부분 UNIQUE | `blog-ownership.md` 7장 |

동시 요청에서 제약 위반이 발생하면 애플리케이션은 이를 잡아 Spec에 정의된 에러 코드로 변환한다.
예: 중복 구독 → `409 ALREADY_SUBSCRIBED`, 동시 Ownership 확인 → `409 BLOG_ALREADY_OWNED`.

### 타입 규칙

- PK/FK: `char(26)` (`006-id-strategy.md`)
- 시각: `timestamptz`
- 열거형: `varchar` + `CHECK` 제약. Postgres enum 타입을 쓰지 않는다.
  값 추가 시 마이그레이션이 단순하다.
- 금액이나 정밀 수치는 이 프로젝트에 없다.

### 인덱스

Feed 조회 정렬이 `(published_at DESC, id DESC)`이므로 그에 맞는 인덱스를 초기 마이그레이션에 넣는다.
(`docs/specs/feed.md` 8장)

---

## 6. 재검토 조건

- 무중단 배포에서 마이그레이션이 잠금 문제를 일으킬 때
- Post 테이블이 커져 인덱스 추가에 `CONCURRENTLY`가 필요할 때
- 여러 서비스가 같은 DB를 공유하게 될 때
