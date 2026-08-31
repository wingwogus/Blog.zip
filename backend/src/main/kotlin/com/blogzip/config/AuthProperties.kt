package com.blogzip.config

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * 인증 설정. docs/decisions/002-auth-strategy.md
 *
 * [jwtSecret]에 기본값을 두지 않는다. 값이 없으면 기동이 실패해야 한다.
 * 기본값이 있으면 운영에서 dummy 키로 도는 사고가 가능하다.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    @field:NotBlank(message = "JWT_SECRET 환경변수가 필요하다")
    val jwtSecret: String = "",
    val accessTokenTtl: Duration = Duration.ofMinutes(30),
    val refreshTokenTtl: Duration = Duration.ofDays(14),
    /** local, test 프로필에서만 false로 둔다. HTTPS가 있는 곳에서는 true를 유지한다. */
    val refreshCookieSecure: Boolean = true,
    @field:Min(1)
    val loginMaxAttempts: Int = 5,
    val loginBlockDuration: Duration = Duration.ofMinutes(10),
)
