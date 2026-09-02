package com.blogzip.auth

import com.blogzip.auth.api.RefreshTokenCookieWriter
import com.blogzip.config.AuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

class RefreshTokenCookieWriterTest {

    private val properties = AuthProperties(
        jwtSecret = "test-only-secret-not-used-in-any-real-environment-0123456789",
        refreshTokenTtl = Duration.ofDays(14),
        refreshCookieSecure = false,
    )
    private val writer = RefreshTokenCookieWriter(properties)

    @Test
    fun `refreshToken 쿠키는 HttpOnly SameSite=Strict 이고 경로가 재발급과 로그아웃뿐이다`() {
        val response = MockHttpServletResponse()
        writer.set(response, "opaque-refresh")

        val cookies = response.getHeaders(HttpHeaders.SET_COOKIE)
        assertThat(cookies).hasSize(2)
        assertThat(cookies).allSatisfy { cookie ->
            assertThat(cookie).contains("refreshToken=opaque-refresh")
            assertThat(cookie).contains("HttpOnly")
            assertThat(cookie).contains("SameSite=Strict")
            assertThat(cookie).doesNotContain("Secure")
        }
        assertThat(cookies.map { it.substringAfter("Path=").substringBefore(';') })
            .containsExactlyInAnyOrder(
                "/api/v1/auth/token/refresh",
                "/api/v1/auth/logout",
            )
    }

    @Test
    fun `clear는 빈 값과 max-age 0으로 만료시킨다`() {
        val response = MockHttpServletResponse()
        writer.clear(response)

        val cookies = response.getHeaders(HttpHeaders.SET_COOKIE)
        assertThat(cookies).hasSize(2)
        assertThat(cookies).allSatisfy { cookie ->
            assertThat(cookie).contains("refreshToken=;")
            assertThat(cookie).contains("Max-Age=0")
            assertThat(cookie).contains("HttpOnly")
            assertThat(cookie).contains("SameSite=Strict")
        }
    }

    @Test
    fun `secure 설정이 켜지면 Secure 속성을 붙인다`() {
        val secureWriter = RefreshTokenCookieWriter(properties.copy(refreshCookieSecure = true))
        val response = MockHttpServletResponse()
        secureWriter.set(response, "opaque-refresh")

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).allSatisfy { cookie ->
            assertThat(cookie).contains("Secure")
        }
    }
}
