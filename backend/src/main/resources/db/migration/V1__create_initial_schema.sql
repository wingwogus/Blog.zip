-- Blog.zip 초기 스키마
--
-- 유일 제약은 비즈니스 규칙 그 자체다. 애플리케이션 검증만으로 두지 않는다.
-- docs/decisions/008-schema-migration.md 5장
--
-- PK/FK는 char(26) ULID. docs/decisions/006-id-strategy.md
-- 시각은 timestamptz. 열거형은 varchar + CHECK.
--
-- 이 파일은 머지 후 수정하지 않는다. 변경은 새 마이그레이션으로 한다.

-- ---------------------------------------------------------------------------
-- users - docs/specs/auth.md
-- ---------------------------------------------------------------------------
CREATE TABLE users
(
    id            char(26)     NOT NULL,
    email         varchar(254) NOT NULL,
    password_hash varchar(72)  NOT NULL,
    nickname      varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    -- AUTH-BR-001: 하나의 이메일로 하나의 계정만. 소문자로 정규화해 저장한다.
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------------
-- refresh_tokens - docs/decisions/002-auth-strategy.md
-- 원문이 아닌 SHA-256 해시를 저장한다.
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens
(
    id         char(26)    NOT NULL,
    user_id    char(26)    NOT NULL,
    token_hash char(64)    NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
-- 만료 토큰 정리 작업용
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------------
-- blogs - docs/specs/blog-subscription.md
-- 사용자와 무관하게 Blog 하나당 한 행만 존재한다.
-- friend_name은 여기에 저장하지 않는다. (BR-004, BR-005)
-- ---------------------------------------------------------------------------
CREATE TABLE blogs
(
    id             char(26)     NOT NULL,
    canonical_url  varchar(2048) NOT NULL,
    site_url       varchar(2048) NOT NULL,
    feed_url       varchar(2048) NOT NULL,
    title          varchar(255) NOT NULL,
    platform       varchar(20)  NOT NULL,
    -- Ownership이 인증된 경우에만 채운다. (BR-008)
    owner_user_id  char(26),
    created_at     timestamptz  NOT NULL,
    CONSTRAINT pk_blogs PRIMARY KEY (id),
    -- FR-005: 정규화된 주소로 중복 Blog를 막는다.
    CONSTRAINT uk_blogs_canonical_url UNIQUE (canonical_url),
    CONSTRAINT fk_blogs_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT ck_blogs_platform CHECK (platform IN ('NAVER', 'VELOG', 'TISTORY', 'GENERIC'))
);

-- ---------------------------------------------------------------------------
-- blog_fetch_states - docs/specs/feed.md FR-011
-- ---------------------------------------------------------------------------
CREATE TABLE blog_fetch_states
(
    blog_id                    char(26)    NOT NULL,
    status                     varchar(20) NOT NULL,
    last_successful_fetch_at   timestamptz,
    last_attempt_at            timestamptz NOT NULL,
    next_attempt_at            timestamptz NOT NULL,
    consecutive_failure_count  int         NOT NULL DEFAULT 0,
    last_failure_reason        varchar(50),
    CONSTRAINT pk_blog_fetch_states PRIMARY KEY (blog_id),
    CONSTRAINT fk_blog_fetch_states_blog FOREIGN KEY (blog_id) REFERENCES blogs (id),
    CONSTRAINT ck_blog_fetch_states_status CHECK (status IN ('ACTIVE', 'FAILING', 'UNAVAILABLE'))
);

-- 수집 대상 선정용. docs/decisions/004-post-collection.md
CREATE INDEX idx_blog_fetch_states_next_attempt ON blog_fetch_states (next_attempt_at);

-- ---------------------------------------------------------------------------
-- subscriptions - docs/specs/blog-subscription.md
-- friend_name은 구독자에게만 적용되는 Label이다. (BR-004, BR-005)
-- ---------------------------------------------------------------------------
CREATE TABLE subscriptions
(
    id          char(26)    NOT NULL,
    user_id     char(26)    NOT NULL,
    blog_id     char(26)    NOT NULL,
    friend_name varchar(20) NOT NULL,
    created_at  timestamptz NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    -- SUB-BR-007: 한 User는 같은 Blog를 두 번 구독할 수 없다.
    CONSTRAINT uk_subscriptions_user_blog UNIQUE (user_id, blog_id),
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_subscriptions_blog FOREIGN KEY (blog_id) REFERENCES blogs (id)
);

CREATE INDEX idx_subscriptions_blog ON subscriptions (blog_id);

-- ---------------------------------------------------------------------------
-- posts - docs/specs/feed.md
-- 본문과 발췌를 저장하지 않는다. docs/decisions/004-post-collection.md
-- ---------------------------------------------------------------------------
CREATE TABLE posts
(
    id                     char(26)      NOT NULL,
    blog_id                char(26)      NOT NULL,
    external_id            varchar(512)  NOT NULL,
    title                  varchar(500)  NOT NULL,
    url                    varchar(2048) NOT NULL,
    published_at           timestamptz   NOT NULL,
    published_at_estimated boolean       NOT NULL DEFAULT false,
    thumbnail_url          varchar(2048),
    collected_at           timestamptz   NOT NULL,
    CONSTRAINT pk_posts PRIMARY KEY (id),
    -- FEED-BR-004: 같은 게시물을 중복 저장하지 않는다.
    CONSTRAINT uk_posts_blog_external UNIQUE (blog_id, external_id),
    CONSTRAINT fk_posts_blog FOREIGN KEY (blog_id) REFERENCES blogs (id)
);

-- Feed 정렬은 published_at DESC, id DESC 안정 정렬이다. docs/specs/feed.md 8장
CREATE INDEX idx_posts_blog_published ON posts (blog_id, published_at DESC, id DESC);

-- ---------------------------------------------------------------------------
-- post_read_states - docs/specs/feed.md FR-009
-- 읽음 상태는 사용자별로 독립적이다. (FEED-BR-005)
-- ---------------------------------------------------------------------------
CREATE TABLE post_read_states
(
    id      char(26)    NOT NULL,
    user_id char(26)    NOT NULL,
    post_id char(26)    NOT NULL,
    read_at timestamptz NOT NULL,
    CONSTRAINT pk_post_read_states PRIMARY KEY (id),
    CONSTRAINT uk_post_read_states_user_post UNIQUE (user_id, post_id),
    CONSTRAINT fk_post_read_states_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_post_read_states_post FOREIGN KEY (post_id) REFERENCES posts (id)
);

-- ---------------------------------------------------------------------------
-- ownerships - docs/specs/blog-ownership.md
-- ---------------------------------------------------------------------------
CREATE TABLE ownerships
(
    id                  char(26)    NOT NULL,
    user_id             char(26)    NOT NULL,
    blog_id             char(26)    NOT NULL,
    verified_at         timestamptz NOT NULL,
    verification_method varchar(30) NOT NULL,
    CONSTRAINT pk_ownerships PRIMARY KEY (id),
    -- OWN-BR-006: 하나의 Blog에는 최대 한 명의 소유자만 존재한다.
    CONSTRAINT uk_ownerships_blog UNIQUE (blog_id),
    -- OWN-BR-007: MVP에서 한 User는 하나의 Blog만 소유한다.
    CONSTRAINT uk_ownerships_user UNIQUE (user_id),
    CONSTRAINT fk_ownerships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ownerships_blog FOREIGN KEY (blog_id) REFERENCES blogs (id),
    CONSTRAINT ck_ownerships_method CHECK (verification_method IN ('POST_CONTENT', 'BLOG_DESCRIPTION'))
);

-- ---------------------------------------------------------------------------
-- ownership_verifications - docs/specs/blog-ownership.md 7장
-- ---------------------------------------------------------------------------
CREATE TABLE ownership_verifications
(
    id              char(26)     NOT NULL,
    user_id         char(26)     NOT NULL,
    blog_id         char(26)     NOT NULL,
    code            varchar(64)  NOT NULL,
    status          varchar(20)  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    attempt_count   int          NOT NULL DEFAULT 0,
    last_attempt_at timestamptz,
    created_at      timestamptz  NOT NULL,
    CONSTRAINT pk_ownership_verifications PRIMARY KEY (id),
    CONSTRAINT fk_ownership_verifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ownership_verifications_blog FOREIGN KEY (blog_id) REFERENCES blogs (id),
    CONSTRAINT ck_ownership_verifications_status CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED'))
);

-- (user_id, blog_id)에 대해 PENDING은 최대 하나만 존재한다.
-- 부분 유일 인덱스는 Hibernate 자동 DDL로 표현할 수 없다. Flyway를 쓰는 이유 중 하나다.
CREATE UNIQUE INDEX uk_ownership_verifications_pending
    ON ownership_verifications (user_id, blog_id)
    WHERE status = 'PENDING';
