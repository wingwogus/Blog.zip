package com.blogzip.auth

import com.blogzip.auth.domain.Email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** docs/specs/auth.md FR-001 — 이메일은 대소문자 구분 없이 저장·비교한다. */
class EmailTest {

    @Test
    fun `앞뒤 공백을 제거하고 소문자로 정규화한다`() {
        assertThat(Email.normalize("  User@Example.COM ")).isEqualTo("user@example.com")
    }

    @Test
    fun `이미 정규화된 값은 그대로 둔다`() {
        assertThat(Email.normalize("user@example.com")).isEqualTo("user@example.com")
    }
}
