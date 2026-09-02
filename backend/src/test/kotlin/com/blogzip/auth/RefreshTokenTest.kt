package com.blogzip.auth

import com.blogzip.auth.domain.RefreshToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class RefreshTokenTest {

    private val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
    private val expiresAt = Instant.parse("2026-01-15T00:00:00Z")

    private fun token(expiresAt: Instant = this.expiresAt): RefreshToken = RefreshToken(
        userId = "01ARZ3NDEKTSV4RRFFQ69G5FAV",
        tokenHash = "a".repeat(RefreshToken.TOKEN_HASH_LENGTH),
        expiresAt = expiresAt,
        createdAt = issuedAt,
    )

    @Test
    fun `만료 시각 이전이고 무효화되지 않으면 활성이다`() {
        val refresh = token()
        val now = Instant.parse("2026-01-10T00:00:00Z")

        assertThat(refresh.isRevoked()).isFalse()
        assertThat(refresh.isExpired(now)).isFalse()
        assertThat(refresh.isActive(now)).isTrue()
    }

    @Test
    fun `만료 시각과 같거나 이후면 만료다`() {
        val refresh = token()

        assertThat(refresh.isExpired(expiresAt)).isTrue()
        assertThat(refresh.isActive(expiresAt)).isFalse()
        assertThat(refresh.isExpired(expiresAt.plusSeconds(1))).isTrue()
    }

    @Test
    fun `revoke는 무효화 시각을 한 번만 기록한다`() {
        val refresh = token()
        val first = Instant.parse("2026-01-02T00:00:00Z")
        val second = Instant.parse("2026-01-03T00:00:00Z")

        refresh.revoke(first)
        refresh.revoke(second)

        assertThat(refresh.revokedAt).isEqualTo(first)
        assertThat(refresh.isRevoked()).isTrue()
        assertThat(refresh.isActive(Instant.parse("2026-01-04T00:00:00Z"))).isFalse()
    }
}
