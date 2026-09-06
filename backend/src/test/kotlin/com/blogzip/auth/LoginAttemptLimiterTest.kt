package com.blogzip.auth

import com.blogzip.auth.service.LoginAttemptLimiter
import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import com.blogzip.auth.config.AuthProperties
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class LoginAttemptLimiterTest {

    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val cache: Cache<String, Any> = Caffeine.newBuilder().maximumSize(100).build()
    private val properties = AuthProperties(
        jwtSecret = "test-only-secret-not-used-in-any-real-environment-0123456789",
        loginMaxAttempts = 5,
        loginBlockDuration = Duration.ofMinutes(10),
    )

    private fun limiter(now: Instant): LoginAttemptLimiter =
        LoginAttemptLimiter(cache, properties, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `연속 5회 실패 후 6번째는 AUTH_006이다`() {
        val limiter = limiter(start)
        repeat(5) { limiter.recordFailure("User@Example.COM") }

        assertThatThrownBy { limiter.assertNotBlocked("user@example.com") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS)
    }

    @Test
    fun `성공하면 실패 횟수가 초기화된다`() {
        val limiter = limiter(start)
        repeat(4) { limiter.recordFailure("user@example.com") }
        limiter.clear("user@example.com")
        limiter.recordFailure("user@example.com")

        assertThatCode { limiter.assertNotBlocked("user@example.com") }.doesNotThrowAnyException()
    }

    @Test
    fun `차단 10분이 지나면 다시 시도할 수 있다`() {
        limiter(start).apply {
            repeat(5) { recordFailure("user@example.com") }
        }

        assertThatCode {
            limiter(start.plus(Duration.ofMinutes(10))).assertNotBlocked("user@example.com")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `이메일은 정규화해 같은 카운터를 쓴다`() {
        val limiter = limiter(start)
        repeat(5) { limiter.recordFailure("  USER@EXAMPLE.COM ") }

        assertThatThrownBy { limiter.assertNotBlocked("user@example.com") }
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS)
    }

    @Test
    fun `차단 직전에는 여전히 AUTH_006이다`() {
        limiter(start).apply { repeat(5) { recordFailure("user@example.com") } }

        assertThatThrownBy {
            limiter(start.plus(Duration.ofMinutes(10)).minusMillis(1))
                .assertNotBlocked("user@example.com")
        }.extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS)
    }

    @Test
    fun `4회 실패는 아직 통과한다`() {
        val limiter = limiter(start)
        repeat(4) { limiter.recordFailure("user@example.com") }
        assertThatCode { limiter.assertNotBlocked("user@example.com") }.doesNotThrowAnyException()
        assertThat(cache.estimatedSize()).isGreaterThan(0)
    }
}
