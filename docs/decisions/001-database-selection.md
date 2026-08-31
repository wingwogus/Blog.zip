# 001. Database 선택 - PostgreSQL

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/` 전체
- 관련 Decision: `006-id-strategy.md`, `008-schema-migration.md`

---

## 1. Context

Blog.zip은 User, Blog, Subscription, Ownership, Post와 그 사이의 관계를 저장한다.

데이터 특성:

- 관계가 명확하고 조인이 필요하다. Feed 조회는 `User → Subscription → Blog → Post`를 거친다.
- 유일성 제약이 비즈니스 규칙 그 자체다. `(userId, blogId)` 유일, `(blogId, externalId)` 유일, `Ownership.blogId` 유일.
  이 제약이 BR-005(한 Blog 여러 이름), 중복 Post 방지, 단일 소유자 규칙을 DB 레벨에서 보장한다.
- Post는 수집으로 계속 쌓인다. 쓰기보다 읽기가 많다.
- 동시성 경합 지점이 있다. 같은 Blog에 대한 Ownership 확인은 한 명만 성공해야 한다.
  (`docs/specs/blog-ownership.md` FR-006)

---

## 2. Decision

**PostgreSQL을 사용한다. 로컬 개발과 테스트 환경도 PostgreSQL을 쓴다.**

- 운영: PostgreSQL 18.x (현재 안정 major)
- 로컬 개발: Docker Compose로 같은 major 버전의 PostgreSQL 컨테이너
- 테스트: Testcontainers로 같은 이미지

접근 기술은 Spring Data JPA + Hibernate, 동적 쿼리는 QueryDSL을 쓴다. (`007-persistence-stack.md`)

### H2를 쓰지 않는 이유

초기에 로컬은 H2로 가려던 방향을 바꿨다. 이유:

- 유일 제약 위반, 잠금 동작, `ON CONFLICT` 동작이 Postgres와 H2에서 다르게 나타난다.
  이 프로젝트에서 그 차이가 걸리는 지점이 정확히 비즈니스 규칙 검증 지점이다.
  중복 구독 방지, 중복 Post 방지, Ownership 단일 소유자는 모두 DB 제약과 경합 처리에 의존한다.
- H2 호환 모드로 통과한 테스트는 Postgres에서 통과한다는 보장이 없다.
  "로컬은 통과했는데 배포하니 깨짐"이 이 카테고리에서 가장 자주 나온다.
- Docker가 이미 개발 환경 전제이므로 Postgres 컨테이너를 띄우는 비용이 H2 대비 낮다.

H2는 스키마와 제약을 쓰지 않는 순수 단위 테스트에서도 필요하지 않다. 그런 테스트는 DB 없이 돌린다.

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| MySQL | 익숙함, 운영 사례 많음 | 부분 인덱스와 표현식 제약이 약하고 타입 처리가 관대해 데이터 오류를 늦게 발견 | Postgres 대비 이 프로젝트에 이점이 없다 |
| MongoDB | 스키마 유연, Feed 문서 저장에 편함 | 다중 컬렉션 유일 제약과 조인이 약함 | 유일 제약과 관계가 이 제품의 핵심 규칙이다 |
| SQLite | 운영 부담 최소 | 동시 쓰기 취약, 관리형 서비스 부재 | 수집 스케줄러와 API 서버가 동시에 쓴다 |
| 로컬 H2 + 운영 Postgres | 로컬 실행 빠름, Docker 불필요 | 제약/경합/SQL 동작 차이로 검증 신뢰도 하락 | 위 "H2를 쓰지 않는 이유" 참고 |

---

## 4. Trade-off

- 로컬 개발에 Docker가 필수가 된다. `docker compose up`이 선행 조건이다.
- 테스트 시작 시 컨테이너 기동 시간이 붙는다. Testcontainers 컨테이너 재사용과 클래스 단위 공유로 완화한다.
- 인메모리 DB의 즉시 기동을 포기한다. 대신 검증 결과가 운영과 같은 엔진에서 나온다.

---

## 5. Consequences

### 스키마에서 활용할 Postgres 기능

- 유일 제약: `(user_id, blog_id)`, `(blog_id, external_id)`, `ownership.blog_id`, `ownership.user_id`
- 부분 유일 인덱스: `(user_id, blog_id) WHERE status = 'PENDING'` — OwnershipVerification의 PENDING 단일성
  (`docs/specs/blog-ownership.md` 7장)
- `timestamptz`: 모든 시각 컬럼. API는 ISO-8601 UTC로 주고받는다. (`docs/specs/README.md`)
- `INSERT ... ON CONFLICT DO NOTHING`: Post 수집 시 중복 방지. (`docs/specs/feed.md` FR-004)

### 테스트

- 통합 테스트는 Testcontainers Postgres에서 실행한다.
- 유일 제약 위반과 경합 케이스를 실제 DB에서 검증한다.
  최소한 다음은 실제 DB로 검증한다: 중복 구독, 중복 Post, 동시 Ownership 확인.
- 도메인 로직 단위 테스트는 DB 없이 돌린다.

### 운영

- 커넥션 풀 크기는 인스턴스 수와 Postgres `max_connections`를 함께 보고 정한다.
- 수집 스케줄러와 API가 같은 인스턴스에서 도는 동안은 풀을 공유한다. 분리 시점에 재검토한다.

---

## 6. 재검토 조건

- 관리형 Postgres 비용이 문제가 될 때
- Post 규모가 커져 파티셔닝이나 별도 저장소가 필요할 때
- 읽기 부하 분산을 위해 replica가 필요할 때
