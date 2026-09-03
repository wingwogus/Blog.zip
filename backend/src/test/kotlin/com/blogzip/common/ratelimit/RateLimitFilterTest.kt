package com.blogzip.common.ratelimit

import com.blogzip.auth.service.AccessJwtProvider
import com.blogzip.common.error.ErrorResponseFactory
import com.blogzip.common.logging.MdcLoggingFilter
import com.blogzip.auth.config.AuthProperties
import com.github.benmanes.caffeine.cache.Caffeine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RateLimitFilterTest {

    private val windowStart = Instant.parse("2026-01-01T00:00:00Z")
    private lateinit var clock: MutableClock
    private lateinit var filter: RateLimitFilter

    @BeforeEach
    fun setUp() {
        clock = MutableClock(windowStart)
        val properties = AuthProperties(
            jwtSecret = "test-only-secret-not-used-in-any-real-environment-0123456789",
        )
        filter = RateLimitFilter(
            limiter = FixedWindowRateLimiter(
                cache = Caffeine.newBuilder().build(),
                clock = clock,
            ),
            accessJwtProvider = AccessJwtProvider(properties, clock),
            errorResponses = ErrorResponseFactory(StaticMessageSource()),
            jsonMapper = JsonMapper.builder().build(),
        )
    }

    @Test
    fun `유효한 Bearer 토큰 사용자는 모든 요청을 분당 120회까지 공유해서 허용한다`() {
        val token = AccessJwtProvider(
            AuthProperties(jwtSecret = "test-only-secret-not-used-in-any-real-environment-0123456789"),
            clock,
        ).issue("user-1")

        repeat(120) {
            assertThat(doFilter("/api/v1/private", "198.51.100.10", token).status).isEqualTo(200)
        }
        val exceeded = doFilter("/api/v1/private", "198.51.100.10", token)

        assertThat(exceeded.status).isEqualTo(429)
        assertThat(exceeded.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("60")
        assertThat(exceeded.contentAsString).contains("COMMON_004")
    }

    @Test
    fun `미인증 IP의 모든 요청은 분당 30회까지 허용한다`() {
        repeat(30) {
            assertThat(doFilter("/api/v1/private", "198.51.100.20").status).isEqualTo(200)
        }
        val exceeded = doFilter("/api/v1/private", "198.51.100.20")

        assertThat(exceeded.status).isEqualTo(429)
        assertThat(exceeded.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("60")
        assertThat(exceeded.contentAsString).contains("COMMON_004")
    }

    @Test
    fun `signup과 login은 미인증 IP에 분당 10회 제한을 추가한다`() {
        repeat(10) {
            assertThat(doFilter(RateLimitFilter.SIGNUP_PATH, "198.51.100.30").status).isEqualTo(200)
        }
        val signupExceeded = doFilter(RateLimitFilter.SIGNUP_PATH, "198.51.100.30")
        assertThat(signupExceeded.status).isEqualTo(429)
        assertThat(signupExceeded.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("60")
        assertThat(signupExceeded.contentAsString).contains("COMMON_004")

        repeat(9) {
            assertThat(doFilter(RateLimitFilter.LOGIN_PATH, "198.51.100.31").status).isEqualTo(200)
        }
        assertThat(doFilter(RateLimitFilter.LOGIN_PATH, "198.51.100.31").status).isEqualTo(200)
        val loginExceeded = doFilter(RateLimitFilter.LOGIN_PATH, "198.51.100.31")
        assertThat(loginExceeded.status).isEqualTo(429)
        assertThat(loginExceeded.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("60")
        assertThat(loginExceeded.contentAsString).contains("COMMON_004")
    }

    @Test
    fun `signup과 login은 같은 미인증 IP의 공유 제한을 사용한다`() {
        repeat(5) { doFilter(RateLimitFilter.SIGNUP_PATH, "198.51.100.35") }
        repeat(5) { doFilter(RateLimitFilter.LOGIN_PATH, "198.51.100.35") }

        val exceeded = doFilter(RateLimitFilter.SIGNUP_PATH, "198.51.100.35")

        assertThat(exceeded.status).isEqualTo(429)
        assertThat(exceeded.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("60")
        assertThat(exceeded.contentAsString).contains("COMMON_004")
    }

    @Test
    fun `invalid and missing token은 IP 제한을 사용한다`() {
        repeat(30) {
            val token = if (it == 0) "not-a-jwt" else null
            assertThat(doFilter("/api/v1/private", "198.51.100.40", token).status).isEqualTo(200)
        }

        assertThat(doFilter("/api/v1/private", "198.51.100.40", "Bearer malformed").status).isEqualTo(429)
    }

    @Test
    fun `토큰 사용자 제한은 IP와 분리되고 remoteAddr만 사용한다`() {
        val token = AccessJwtProvider(
            AuthProperties(jwtSecret = "test-only-secret-not-used-in-any-real-environment-0123456789"),
            clock,
        ).issue("user-2")

        repeat(30) { doFilter("/api/v1/private", "198.51.100.50") }
        assertThat(doFilter("/api/v1/private", "198.51.100.50", token).status).isEqualTo(200)
        assertThat(doFilter("/api/v1/private", "198.51.100.50", token, "203.0.113.50").status).isEqualTo(200)
        assertThat(doFilter("/api/v1/private", "198.51.100.50", "Bearer invalid", "203.0.113.50").status).isEqualTo(429)
    }

    @Test
    fun `제한 필터는 chain보다 먼저 실행된다`() {
        val request = MockHttpServletRequest("GET", "/api/v1/private").apply {
            remoteAddr = "198.51.100.60"
        }
        val response = MockHttpServletResponse()
        var chainCalled = false
        val chain = object : FilterChain {
            override fun doFilter(request: ServletRequest, response: ServletResponse) {
                chainCalled = true
            }
        }

        filter.doFilter(request, response, chain)

        assertThat(chainCalled).isTrue()
        assertThat(MdcLoggingFilter::class.java.getAnnotation(Order::class.java).value)
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE)
        assertThat(RateLimitFilter::class.java.getAnnotation(Order::class.java).value)
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1)
    }

    private fun doFilter(
        path: String,
        remoteAddr: String,
        token: String? = null,
        forwardedFor: String? = null,
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", path).apply {
            this.remoteAddr = remoteAddr
            token?.let { addHeader("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
        }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, object : FilterChain {
            override fun doFilter(request: ServletRequest, response: ServletResponse) = Unit
        })
        return response
    }

    private class MutableClock(initialInstant: Instant) : Clock() {
        private var current: Instant = initialInstant

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
