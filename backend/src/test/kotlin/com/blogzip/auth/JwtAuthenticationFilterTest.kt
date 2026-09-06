package com.blogzip.auth

import com.blogzip.auth.service.AccessJwtProvider
import com.blogzip.auth.service.JwtAuthenticationFilter
import com.blogzip.common.error.ErrorCode
import com.blogzip.common.error.ErrorResponseFactory
import com.blogzip.common.id.Ulid
import com.blogzip.common.logging.LoggingUtil
import com.blogzip.auth.config.AuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class JwtAuthenticationFilterTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = AuthProperties(
        jwtSecret = "test-only-secret-not-used-in-any-real-environment-0123456789",
        accessTokenTtl = Duration.ofMinutes(30),
    )
    private val provider = AccessJwtProvider(properties, clock)
    private val filter = JwtAuthenticationFilter(
        provider,
        ErrorResponseFactory(StaticMessageSource()),
        JacksonJsonHttpMessageConverter(),
    )

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    @Test
    fun `유효한 Bearer 토큰이면 SecurityContext와 MDC userId를 채운다`() {
        val userId = Ulid.generate()
        val request = MockHttpServletRequest()
        request.servletPath = "/api/v1/users/me"
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ${provider.issue(userId)}")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication).isNotNull()
        assertThat(authentication!!.isAuthenticated).isTrue()
        assertThat(authentication.principal).isEqualTo(userId)
        assertThat(MDC.get(LoggingUtil.MDC_USER_ID)).isEqualTo(userId)
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `토큰이 없으면 인증 없이 통과한다`() {
        val request = MockHttpServletRequest()
        request.servletPath = "/api/v1/users/me"
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(MDC.get(LoggingUtil.MDC_USER_ID)).isNull()
    }

    @Test
    fun `만료된 토큰은 AUTH_001로 막는다`() {
        val token = provider.issue(Ulid.generate())
        val expired = AccessJwtProvider(
            properties,
            Clock.fixed(now.plus(Duration.ofMinutes(31)), ZoneOffset.UTC),
        )
        val expiredFilter = JwtAuthenticationFilter(
            expired,
            ErrorResponseFactory(StaticMessageSource()),
            JacksonJsonHttpMessageConverter(),
        )
        val request = MockHttpServletRequest()
        request.servletPath = "/api/v1/users/me"
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        val response = MockHttpServletResponse()

        expiredFilter.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(401)
        assertThat(response.contentAsString).contains(ErrorCode.UNAUTHORIZED.code)
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(response.contentAsString).doesNotContain(token)
    }

    @Test
    fun `형식이 잘못된 토큰은 AUTH_007로 막는다`() {
        val request = MockHttpServletRequest()
        request.servletPath = "/api/v1/users/me"
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(400)
        assertThat(response.contentAsString).contains(ErrorCode.MALFORMED_JWT.code)
        assertThat(response.contentType).startsWith(MediaType.APPLICATION_JSON_VALUE)
    }

    @Test
    fun `재발급 경로는 만료된 accessToken이 있어도 통과한다`() {
        val token = provider.issue(Ulid.generate())
        val expired = AccessJwtProvider(
            properties,
            Clock.fixed(now.plus(Duration.ofMinutes(31)), ZoneOffset.UTC),
        )
        val expiredFilter = JwtAuthenticationFilter(
            expired,
            ErrorResponseFactory(StaticMessageSource()),
            JacksonJsonHttpMessageConverter(),
        )
        val request = MockHttpServletRequest()
        request.servletPath = "/api/v1/auth/token/refresh"
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        val response = MockHttpServletResponse()

        expiredFilter.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(200)
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }
}
