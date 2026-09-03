package com.blogzip.auth.controller

import com.blogzip.auth.config.AuthProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration
import jakarta.servlet.http.HttpServletResponse

/**
 * refreshToken을 HttpOnly + SameSite=Strict 쿠키로 심는다.
 * 경로는 재발급과 로그아웃으로만 제한한다.
 * docs/decisions/002-auth-strategy.md
 */
@Component
class RefreshTokenCookieWriter(
    private val properties: AuthProperties,
) {

    fun set(response: HttpServletResponse, rawToken: String) {
        write(response, rawToken, properties.refreshTokenTtl)
    }

    fun clear(response: HttpServletResponse) {
        write(response, "", Duration.ZERO)
    }

    private fun write(response: HttpServletResponse, value: String, maxAge: Duration) {
        for (path in PATHS) {
            val cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build()
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        }
    }

    companion object {
        const val COOKIE_NAME = "refreshToken"
        val PATHS = listOf("/api/v1/auth/token/refresh", "/api/v1/auth/logout")
    }
}
