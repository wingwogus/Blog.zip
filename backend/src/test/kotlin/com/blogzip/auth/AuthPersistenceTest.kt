package com.blogzip.auth

import com.blogzip.auth.service.AuthService
import com.blogzip.auth.domain.RefreshToken
import com.blogzip.auth.domain.User
import com.blogzip.auth.repository.RefreshTokenRepository
import com.blogzip.auth.repository.UserRepository
import com.blogzip.auth.service.RefreshTokenHasher
import com.blogzip.common.id.Ulid
import com.blogzip.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class AuthPersistenceTest : IntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `정규화된 이메일로 저장하고 조회한다`() {
        val user = userRepository.save(
            User(email = "User@Example.COM", passwordHash = "x".repeat(60), nickname = "재현"),
        )

        assertThat(user.email).isEqualTo("user@example.com")
        assertThat(userRepository.findByEmail("user@example.com")?.getId()).isEqualTo(user.getId())
        assertThat(userRepository.existsByEmail("user@example.com")).isTrue()
        assertThat(userRepository.findByEmail("User@Example.COM")).isNull()
    }

    @Test
    fun `refreshToken은 해시로 저장하고 무효화할 수 있다`() {
        val user = userRepository.save(
            User(email = "owner@example.com", passwordHash = "x".repeat(60), nickname = "소유자"),
        )
        val raw = "opaque-refresh-token"
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val saved = refreshTokenRepository.save(
            RefreshToken(
                userId = user.getId(),
                tokenHash = RefreshTokenHasher.hash(raw),
                expiresAt = now.plusSeconds(14 * 24 * 60 * 60),
                createdAt = now,
            ),
        )

        val found = refreshTokenRepository.findByTokenHash(RefreshTokenHasher.hash(raw))
        assertThat(found?.getId()).isEqualTo(saved.getId())
        assertThat(found?.userId).isEqualTo(user.getId())
        assertThat(found?.isActive(now.plusSeconds(1))).isTrue()

        found!!.revoke(now.plusSeconds(2))
        refreshTokenRepository.save(found)

        val revoked = refreshTokenRepository.findByTokenHash(RefreshTokenHasher.hash(raw))
        assertThat(revoked?.isRevoked()).isTrue()
        assertThat(refreshTokenRepository.findAllByUserId(user.getId())).hasSize(1)
        assertThat(saved.tokenHash).isNotEqualTo(raw)
        assertThat(saved.tokenHash).isEqualTo(RefreshTokenHasher.hash(raw))
    }

    @Test
    fun `비밀번호는 BCrypt 해시로만 저장되고 원문으로 복원할 수 없다`() {
        val email = "hash-${Ulid.generate().lowercase()}@example.com"
        val password = "password1234"

        authService.signup(email, password, "재현")

        val stored = userRepository.findByEmail(email) ?: error("가입된 사용자가 없다")
        assertThat(stored.passwordHash).isNotEqualTo(password)
        assertThat(stored.passwordHash).doesNotContain(password)
        assertThat(stored.passwordHash).startsWith("\$2")
        assertThat(passwordEncoder.matches(password, stored.passwordHash)).isTrue()
        assertThat(passwordEncoder.matches("wrong-password", stored.passwordHash)).isFalse()
    }
}
