# 002. 인증 전략 - JWT + 자동 재발급

## 0. 문서 정보

- Status: Accepted
- Date: 2026-08-30
- 관련 Spec: `docs/specs/auth.md`
- 관련 Decision: `009-ephemeral-state.md`, `010-api-response-contract.md`

---

## 1. Context

`docs/specs/auth.md`는 토큰 발급, 재발급, 로그아웃, 로그인 시도 제한을 요구하고 구체적인 값은 이 Decision으로 넘겼다.

정할 것: 토큰 만료 시간, 저장 위치, 재발급 흐름, 로그인 시도 제한값, API 요청 제한.

---

## 2. Decision

### 토큰

| 항목 | 값 |
| --- | --- |
| accessToken 만료 | 30분 |
| refreshToken 만료 | 14일 |
| 서명 알고리즘 | HS256 |
| 시크릿 | 환경변수 `JWT_SECRET`. **기본값을 두지 않는다** |

시크릿이 없으면 애플리케이션 기동을 실패시킨다. 기본값이 있으면 운영에서 dummy 키로 도는 사고가 가능하다.

accessToken 클레임에 담는 것: `sub`(userId), `iat`, `exp`. 그 외 사용자 정보를 담지 않는다. 토큰은 변경할 수 없으므로 담은 정보가 낡는다.

### 저장 위치

| 토큰 | 클라이언트 저장 | 서버 저장 |
| --- | --- | --- |
| accessToken | 메모리 (JS 변수). localStorage 금지 | 저장하지 않음 (stateless 검증) |
| refreshToken | `HttpOnly` + `Secure` + `SameSite=Strict` 쿠키 | PostgreSQL |

- accessToken을 localStorage에 두지 않는다. XSS 하나로 탈취된다. 새로고침 시 사라지는 건 재발급으로 복구한다.
- refreshToken을 쿠키로 두는 이유는 JS가 접근할 수 없게 하는 것이다. ChamChamCham도 같은 방식이다.
- `Secure`는 `local`, `test` 프로필에서만 끈다. 설정 키는 `app.auth.refresh-cookie-secure`.
- 쿠키 경로는 재발급과 로그아웃 엔드포인트로 제한한다.

refreshToken을 DB에 저장하는 이유는 `009-ephemeral-state.md` 2장에 있다. 요약하면 인메모리면 배포마다 전원 로그아웃된다.

DB에는 토큰 원문을 저장하지 않는다. SHA-256 해시를 저장한다. DB가 유출돼도 토큰을 그대로 쓸 수 없게 한다.

### 자동 재발급

accessToken이 만료되면 클라이언트가 자동으로 재발급하고 원래 요청을 재시도한다. 사용자는 만료를 인지하지 못한다.

```text
API 요청
↓
401 AUTH_001
↓
POST /api/v1/auth/token/refresh  (쿠키의 refreshToken 사용)
↓
성공 → 새 accessToken으로 원래 요청 1회 재시도
실패 → 로그인 화면
```

클라이언트 구현 규칙:

- **재시도는 1회만 한다.** 재발급 후 재시도가 또 401이면 로그인으로 보낸다. 무한 루프를 만들지 않는다.
- **재발급 요청은 단일화(single-flight)한다.** 동시에 여러 요청이 401을 받으면 재발급을 한 번만 호출하고 나머지는 그 결과를 기다린다. 그러지 않으면 회전된 토큰이 서로를 무효화한다.
- 재발급 엔드포인트 자체의 401에는 재시도하지 않는다.

### refreshToken 회전

재발급 성공 시 새 refreshToken을 발급하고 기존 것을 무효화한다.

- 이미 무효화된 refreshToken이 다시 사용되면 **해당 사용자의 모든 refreshToken을 무효화한다.** 탈취 가능성이 있는 상황이다.
- 이 경우도 응답은 `401 AUTH_005`다. 재사용 감지를 별도 코드로 알리지 않는다. 공격자에게 정보를 주지 않는다.

### 로그인 시도 제한

| 항목 | 값 |
| --- | --- |
| 임계값 | 연속 실패 5회 |
| 차단 시간 | 10분 |
| 카운터 기준 | 정규화된 이메일 |
| 카운터 윈도우 | 10분 |
| 초기화 조건 | 로그인 성공 |

- 차단 중 요청은 `429 AUTH_006`이다.
- **차단 상태에서도 이메일 존재 여부를 노출하지 않는다.** 등록되지 않은 이메일도 같은 방식으로 카운트하고 차단한다.
- 카운터는 인메모리다. (`009-ephemeral-state.md`) 재시작 시 초기화되며, 공격자가 재시작을 유발할 수단은 없다.

이메일 단위로만 세면 한 공격자가 여러 이메일을 시도하는 것을 막지 못한다. 그건 아래 IP 기준 제한이 담당한다.

### API 요청 제한

| 대상 | 제한 | 초과 시 |
| --- | --- | --- |
| 인증된 사용자 전체 요청 | 분당 120회 | `429 COMMON_004` |
| 미인증 요청 (IP 기준) | 분당 30회 | `429 COMMON_004` |
| 로그인 / 회원가입 (IP 기준) | 분당 10회 | `429 COMMON_004` |
| Blog 탐색 | 분당 10회 | `429 BLOG_006` (`003-blog-discovery.md`) |
| Ownership 확인 | 분당 5회 | `429 OWNERSHIP_007` |

- 알고리즘: 고정 윈도우 카운터. 슬라이딩 윈도우가 정확하지만 이 규모에서 필요 없다.
- 저장: 인메모리 Caffeine. 인스턴스가 1대라는 전제에 의존한다. (`009-ephemeral-state.md` 4장)
- 제한 초과 응답에 `Retry-After` 헤더를 넣는다.
- 필터 순서: 요청 제한 → 인증. 미인증 요청도 제한 대상이다.
- IP는 프록시 뒤에서 `X-Forwarded-For`를 신뢰해야 한다. **신뢰할 프록시를 명시적으로 설정한 뒤에만 그 헤더를 읽는다.** 아무 요청의 헤더를 그대로 믿으면 제한을 우회할 수 있다.

### 비밀번호

- 해시: BCrypt (Spring Security `BCryptPasswordEncoder`, strength 10)
- 저장은 해시만. 원문과 복호화 가능한 형태를 저장하지 않는다.

### 로그아웃

- 요청의 refreshToken을 무효화하고 쿠키를 만료시킨다.
- 이미 무효한 토큰으로 요청해도 `204`다. (`auth.md` FR-004)

---

## 3. Alternatives

| 대안 | 장점 | 단점 | 선택하지 않은 이유 |
| --- | --- | --- | --- |
| 세션 기반 인증 | 무효화가 즉시, 구현 단순 | 인스턴스 확장 시 세션 저장소 필요 | 팀이 JWT를 쓰고 있고 템플릿도 JWT다 |
| accessToken을 localStorage에 저장 | 새로고침에도 유지, 구현 쉬움 | XSS 하나로 탈취 | 위험 대비 이득이 없다 |
| refreshToken도 응답 본문으로 전달 | 모바일 클라이언트와 동일 처리 | JS가 접근 가능해 XSS 노출 | 웹 클라이언트만 있으므로 쿠키가 낫다 |
| accessToken 만료를 길게 (수 시간) | 재발급 빈도 감소 | 탈취 시 유효 기간이 길다 | 자동 재발급이 있으므로 짧게 둬도 불편하지 않다 |
| refreshToken 회전 없음 | 구현 단순 | 탈취된 토큰이 만료까지 유효 | 회전 비용이 낮다 |
| Redis로 rate limit | 인스턴스 확장에 안전 | 컴포넌트 추가 | 단일 인스턴스에서 이득 없음 (`009`) |

---

## 4. Trade-off

- accessToken이 stateless라 **로그아웃 후에도 최대 30분간 유효하다.** 즉시 무효화가 필요하면 블랙리스트가 필요한데, 그건 stateless의 이점을 버리는 것이다. 30분을 수용한다.
- accessToken을 메모리에 두므로 새로고침마다 재발급이 한 번 발생한다. 사용자 체감 지연은 재발급 1회분이다.
- 요청 제한이 인메모리라 인스턴스를 늘리면 실질 허용량이 인스턴스 수만큼 늘어난다. `009-ephemeral-state.md` 4장에 같은 제약이 기록돼 있다.
- 로그인 시도 카운터가 재시작 시 초기화된다. 배포 중 공격 창이 생기지만, 배포 시점을 공격자가 알 수 없다.
- HttpOnly 쿠키를 쓰므로 CORS에 `credentials`가 필요하고 허용 origin을 명시해야 한다. 와일드카드 origin은 쓸 수 없다.

---

## 5. Consequences

### 구현 구성

- `JwtAuthenticationFilter`: accessToken 검증. 템플릿에서 가져온다. (`011-backend-module-structure.md`)
- `RateLimitFilter`: 인증 필터보다 앞에 둔다.
- `RefreshToken` 엔티티: `userId`, `tokenHash`, `expiresAt`, `revokedAt`. `tokenHash` UNIQUE.
- 만료된 refreshToken 정리는 주기 작업으로 삭제한다. 무한히 쌓이게 두지 않는다.

### 검증할 것

`auth.md` Acceptance Criteria에 이미 있는 항목 외에 다음을 테스트한다.

- 재발급 성공 시 이전 refreshToken이 무효가 된다.
- 무효화된 refreshToken 재사용 시 해당 사용자의 모든 refreshToken이 무효화된다.
- 존재하지 않는 이메일과 잘못된 비밀번호의 실패 응답이 code와 status 모두 동일하다.
- 5회 실패 후 6번째 요청이 `429 AUTH_006`이다.
- 요청 제한 초과 응답에 `Retry-After`가 있다.

시간 의존 테스트는 고정 sleep을 쓰지 않는다. `Clock`을 주입해 시간을 제어한다.

### 로그

토큰 원문, 비밀번호, 쿠키 값을 로그에 남기지 않는다. 사용자 식별은 MDC의 `userId`를 쓴다.

---

## 6. 재검토 조건

- 인스턴스를 2대 이상으로 늘릴 때 (요청 제한과 카운터를 공유 저장소로)
- 모바일 클라이언트가 추가될 때 (쿠키 대신 본문 전달 필요)
- 로그아웃 즉시 무효화 요구가 생길 때 (accessToken 블랙리스트)
- 소셜 로그인을 도입할 때
