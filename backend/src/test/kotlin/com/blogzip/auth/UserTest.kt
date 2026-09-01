package com.blogzip.auth

import com.blogzip.auth.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class UserTest {

    @Test
    fun `생성 시 이메일을 정규화하고 nickname 앞뒤 공백을 제거한다`() {
        val createdAt = Instant.parse("2026-08-30T09:12:00Z")
        val user = User(
            email = "  User@Example.COM ",
            passwordHash = "hashed",
            nickname = " 재현 ",
            createdAt = createdAt,
        )

        assertThat(user.email).isEqualTo("user@example.com")
        assertThat(user.nickname).isEqualTo("재현")
        assertThat(user.passwordHash).isEqualTo("hashed")
        assertThat(user.createdAt).isEqualTo(createdAt)
        assertThat(user.getId()).hasSize(26)
        assertThat(user.isNew).isTrue()
    }

    @Test
    fun `같은 ID면 동등하다`() {
        val user = User("a@example.com", "h", "a")
        assertThat(user).isEqualTo(user)
        assertThat(user).isNotEqualTo(User("a@example.com", "h", "a"))
    }
}
