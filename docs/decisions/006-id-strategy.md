# 006. 식별자 전략 - ULID

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/` 전체 (모든 Spec의 7장 데이터)
- 관련 Decision: `001-database-selection.md`

---

## 1. Context

모든 엔티티(User, Blog, Subscription, Post, Ownership, OwnershipVerification)에 식별자가 필요하다.

제약과 요구사항:

- Spec의 API 예시는 이미 `sub_01H...` 형태의 문자열 ID를 사용한다.
- Feed는 커서 기반 페이지네이션을 쓰고, 정렬 tie-breaker로 ID를 사용한다.
  (`docs/specs/feed.md` 8장: `publishedAt` 내림차순, 동일 시각이면 `postId` 내림차순)
  따라서 ID에 생성 순서가 반영되어야 tie-breaker가 안정적이다.
- Post는 수집으로 계속 쌓이는 테이블이라 삽입 성능과 인덱스 지역성이 의미 있다.
- 순차 증가 정수 ID는 URL과 API에 노출될 때 전체 데이터 규모와 생성 속도를 추측할 수 있다.

---

## 2. Decision

**ULID를 애플리케이션에서 생성하고, Postgres에는 canonical 26자 문자열(`char(26)`)로 저장한다.**

### 저장

- 컬럼 타입: `char(26)`
- 값: Crockford Base32 canonical 표현 (대문자, 26자)
- 접두사(`sub_`, `pst_` 등)는 **저장하지 않는다.**

### API 노출

- API에서는 엔티티 접두사를 붙여 노출한다.

| 엔티티 | 접두사 |
| --- | --- |
| User | `usr_` |
| Blog | `blg_` |
| Subscription | `sub_` |
| Post | `pst_` |
| Ownership | `own_` |
| OwnershipVerification | `own_ver_` |

- 접두사는 직렬화 경계에서만 붙이고 떼며, 도메인과 DB는 26자 ULID만 다룬다.
- 요청으로 들어온 ID는 접두사가 기대값과 다르면 거부한다. 다른 엔티티의 ID를 잘못된 경로에 넣는 실수를 조기에 잡는다.

### 생성

- ID는 **애플리케이션에서 생성한다.** `@GeneratedValue`를 쓰지 않는다.
- 엔티티 생성 시점에 ID를 할당한다. 저장 전에도 ID가 있다.

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| `bigserial` (auto increment) | 가장 단순, 8바이트, 인덱스 최소 | 노출 시 규모 추측 가능, 저장 전 ID 없음, 분산 생성 불가 | API에 그대로 노출하기 부담스럽고 저장 전 ID가 필요한 흐름이 있다 |
| UUID v4 | 표준, 라이브러리 풍부, 충돌 걱정 없음 | 완전 랜덤이라 B-tree 삽입 지역성이 나쁘고 시간 순서가 없다 | Post 삽입이 잦고 ID를 정렬 tie-breaker로 쓴다 |
| UUID v7 | 시간 순서 있음, 표준(RFC 9562), Postgres `uuid` 타입 사용 가능 | 문자열 표현이 ULID보다 길고 사람이 읽기 어렵다 | 기술적으로 거의 동등. ULID의 26자 표현이 로그와 URL에서 더 짧고 다루기 쉬워 ULID를 택했다 |
| ULID를 Postgres `uuid` 타입(16바이트)으로 저장 | 10바이트 절약, 인덱스 크기 감소 | psql에서 UUID 포맷으로 보여 API의 26자 ID와 눈으로 대조가 안 된다 | 디버깅 비용이 10바이트보다 비싸다고 판단했다 |

---

## 4. Trade-off

- `char(26)`은 `uuid`(16바이트)보다 행당 10바이트, 인덱스당 10바이트를 더 쓴다. MVP 규모에서 문제되지 않는다고 판단했다.
- ULID canonical 문자열은 사전순 정렬이 생성 시각 순서와 일치하므로, 문자열로 저장해도 시간 순 인덱스 지역성은 유지된다.
- 접두사 변환 계층이 하나 생긴다. 대신 로그에서 ID만 보고 어떤 엔티티인지 알 수 있다.
- ULID는 같은 밀리초 내 생성 순서를 보장하지 않는다. 밀리초 단위 이하의 정확한 생성 순서가 필요한 요구사항이 생기면 이 결정으로 해결되지 않는다.

---

## 5. Consequences

### JPA에서 주의할 점

ID를 애플리케이션이 할당하므로 엔티티는 항상 non-null ID를 가진다. 이때 Spring Data JPA의 `save()`는 새 엔티티를 신규로 인식하지 못하고 `merge()`를 호출한다. 결과적으로 INSERT마다 불필요한 SELECT가 한 번 더 나간다.

대응 중 하나를 반드시 적용한다.

- 엔티티가 `Persistable<String>`을 구현하고 `@Transient` 신규 플래그로 `isNew()`를 제어한다.
- 또는 저장 경로에서 `EntityManager.persist()`를 직접 호출한다.

이 부분은 조용히 성능만 나빠지는 문제라 리뷰에서 확인한다.

### 스키마

- 모든 PK와 FK는 `char(26)`이다.
- FK 컬럼도 같은 타입이어야 인덱스가 정상 동작한다.
- Flyway 마이그레이션에서 타입을 일관되게 쓴다. (`001-database-selection.md`, `008-schema-migration.md`)

### 커서 페이지네이션

- Feed 커서는 `(publishedAt, postId)` 복합 값을 인코딩한다. ULID의 정렬 특성 덕분에 문자열 비교로 tie-breaker가 성립한다.
- 커서 값은 클라이언트가 해석하지 않는다. (`docs/specs/README.md` 공통 규약)

---

## 6. 재검토 조건

- 단일 인스턴스를 벗어나 ID 생성 노드가 여러 개가 되고 같은 밀리초 충돌이 실제로 관측될 때
- Post 테이블이 커져 인덱스 크기가 실제 병목이 될 때 (`uuid` 16바이트 전환 검토)
- 외부에 ID를 노출하지 않는 방향으로 API가 바뀔 때
