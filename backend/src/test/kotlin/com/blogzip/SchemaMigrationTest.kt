package com.blogzip

import com.blogzip.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Flyway 마이그레이션이 실제 PostgreSQL에서 실행되고,
 * Spec이 요구하는 제약이 DB 레벨에 존재하는지 확인한다.
 *
 * 유일 제약은 비즈니스 규칙 그 자체이므로 애플리케이션 검증만으로 두지 않는다.
 * docs/decisions/008-schema-migration.md 5장
 *
 * ddl-auto=validate이므로 이 테스트가 통과하면 엔티티와 스키마가 어긋나지 않았다는 뜻도 된다.
 */
class SchemaMigrationTest : IntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `마이그레이션이 적용되어 테이블이 생성된다`() {
        val tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String::class.java,
        )

        assertThat(tables).contains(
            "users",
            "refresh_tokens",
            "blogs",
            "blog_fetch_states",
            "subscriptions",
            "posts",
            "post_read_states",
            "ownerships",
            "ownership_verifications",
        )
    }

    @Test
    fun `Spec이 요구하는 유일 제약이 존재한다`() {
        val constraints = jdbcTemplate.queryForList(
            """
            SELECT conname FROM pg_constraint
            WHERE contype = 'u'
              AND connamespace = 'public'::regnamespace
            """.trimIndent(),
            String::class.java,
        )

        assertThat(constraints).contains(
            "uk_users_email",
            "uk_subscriptions_user_blog",
            "uk_posts_blog_external",
            "uk_post_read_states_user_post",
            "uk_ownerships_blog",
            "uk_ownerships_user",
            "uk_blogs_canonical_url",
            "uk_refresh_tokens_token_hash",
        )
    }

    @Test
    fun `PENDING verification 부분 유일 인덱스가 존재한다`() {
        // Hibernate 자동 DDL로 표현할 수 없는 제약이다. Flyway를 쓰는 이유 중 하나.
        val definition = jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_ownership_verifications_pending'",
            String::class.java,
        )

        assertThat(definition)
            .contains("UNIQUE")
            .contains("user_id")
            .contains("blog_id")
            .contains("PENDING")
    }

    @Test
    fun `식별자 컬럼은 char 26이다`() {
        val length = jdbcTemplate.queryForObject(
            """
            SELECT character_maximum_length FROM information_schema.columns
            WHERE table_name = 'subscriptions' AND column_name = 'id'
            """.trimIndent(),
            Int::class.java,
        )

        assertThat(length).isEqualTo(26)
    }

    @Test
    fun `시각 컬럼은 timestamptz다`() {
        val type = jdbcTemplate.queryForObject(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_name = 'posts' AND column_name = 'published_at'
            """.trimIndent(),
            String::class.java,
        )

        assertThat(type).isEqualTo("timestamp with time zone")
    }
}
