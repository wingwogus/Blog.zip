package com.blogzip.auth

import com.blogzip.auth.service.AccessJwtProvider
import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import com.blogzip.common.id.Ulid
import com.blogzip.auth.config.AuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class AccessJwtProviderTest {

    private val secret = "test-only-secret-not-used-in-any-real-environment-0123456789"
    private val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(issuedAt, ZoneOffset.UTC)
    private val properties = AuthProperties(
        jwtSecret = secret,
        accessTokenTtl = Duration.ofMinutes(30),
    )
    private val provider = AccessJwtProvider(properties, clock)

    @Test
    fun `발급한 토큰은 sub iat exp만 담고 다시 읽을 수 있다`() {
        val userId = Ulid.generate()
        val token = provider.issue(userId)
        val parsed = provider.parse(token)

        assertThat(parsed.userId).isEqualTo(userId)
        assertThat(parsed.issuedAt).isEqualTo(issuedAt)
        assertThat(parsed.expiresAt).isEqualTo(issuedAt.plus(Duration.ofMinutes(30)))
        assertThat(payloadKeys(token)).containsExactlyInAnyOrder("sub", "iat", "exp")
        assertThat(headerAlg(token)).isEqualTo("HS256")
    }

    @Test
    fun `만료된 토큰은 AUTH_001이다`() {
        val token = provider.issue(Ulid.generate())
        val expiredClock = Clock.fixed(issuedAt.plus(Duration.ofMinutes(31)), ZoneOffset.UTC)
        val reader = AccessJwtProvider(properties, expiredClock)

        assertThatThrownBy { reader.parse(token) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.UNAUTHORIZED)
    }

    @Test
    fun `형식이 잘못된 토큰은 AUTH_007이다`() {
        assertThatThrownBy { provider.parse("not-a-jwt") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.MALFORMED_JWT)
    }

    @Test
    fun `빈 토큰은 AUTH_007이다`() {
        assertThatThrownBy { provider.parse("") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.MALFORMED_JWT)
    }

    @Test
    fun `서명이 다른 토큰은 AUTH_001이다`() {
        val token = provider.issue(Ulid.generate())
        val other = AccessJwtProvider(
            AuthProperties(jwtSecret = "other-secret-not-used-in-any-real-environment-0123456789"),
            clock,
        )

        assertThatThrownBy { other.parse(token) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.UNAUTHORIZED)
    }

    @Test
    fun `빈 시크릿은 기본값 없이 실패한다`() {
        assertThatThrownBy { AccessJwtProvider(AuthProperties(jwtSecret = "")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("JWT_SECRET 환경변수가 필요하다")
    }

    @Test
    fun `공백만 있는 시크릿도 실패한다`() {
        assertThatThrownBy { AccessJwtProvider(AuthProperties(jwtSecret = "   ")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("JWT_SECRET 환경변수가 필요하다")
    }

    @Test
    fun `만료 직전에는 파싱된다`() {
        val token = provider.issue(Ulid.generate())
        val justBeforeExpiry = Clock.fixed(
            issuedAt.plus(Duration.ofMinutes(30)).minusMillis(1),
            ZoneOffset.UTC,
        )

        val parsed = AccessJwtProvider(properties, justBeforeExpiry).parse(token)
        assertThat(parsed.userId).isNotBlank()
        assertThat(parsed.expiresAt).isEqualTo(issuedAt.plus(Duration.ofMinutes(30)))
    }

    private fun payloadKeys(token: String): Set<String> {
        val json = decodeJwtPart(token, 1)
        return Regex("\"([^\"]+)\"\\s*:").findAll(json).map { it.groupValues[1] }.toSet()
    }

    private fun headerAlg(token: String): String {
        val json = decodeJwtPart(token, 0)
        return Regex("\"alg\"\\s*:\\s*\"([^\"]+)\"").find(json)!!.groupValues[1]
    }

    private fun decodeJwtPart(token: String, index: Int): String {
        val part = token.split(".")[index]
        val padded = part + "=".repeat((4 - part.length % 4) % 4)
        return String(Base64.getUrlDecoder().decode(padded))
    }
}
