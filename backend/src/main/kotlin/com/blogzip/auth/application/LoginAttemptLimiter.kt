package com.blogzip.auth.application

import com.blogzip.auth.domain.Email
import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import com.blogzip.config.AuthProperties
import com.github.benmanes.caffeine.cache.Cache
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * 정규화된 이메일 기준 로그인 실패 카운터.
 *
 * 연속 5회 실패하면 10분간 요청을 거부한다. 등록되지 않은 이메일도 같은 방식으로 센다.
 * 시간은 [Clock]으로 판단한다. Caffeine TTL에 의존하지 않는다.
 * docs/specs/auth.md FR-006, docs/decisions/002-auth-strategy.md
 */
@Component
class LoginAttemptLimiter(
    @param:Qualifier("rateLimitCache")
    private val cache: Cache<String, Any>,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    fun assertNotBlocked(email: String) {
        val state = current(email) ?: return
        if (state.isBlocked(Instant.now(clock))) {
            throw BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS)
        }
    }

    fun recordFailure(email: String) {
        val now = Instant.now(clock)
        val previous = current(email)
        val window = properties.loginBlockDuration
        val stillCounting = previous != null &&
            !previous.isBlocked(now) &&
            now.isBefore(previous.windowStart.plus(window))
        val count = if (stillCounting) previous.failureCount + 1 else 1
        val windowStart = if (stillCounting) previous.windowStart else now
        val blockedUntil =
            if (count >= properties.loginMaxAttempts) now.plus(window) else null
        cache.put(key(email), LoginAttemptState(count, windowStart, blockedUntil))
    }

    fun clear(email: String) {
        cache.invalidate(key(email))
    }

    private fun current(email: String): LoginAttemptState? =
        cache.getIfPresent(key(email)) as LoginAttemptState?

    private fun key(email: String): String = PREFIX + Email.normalize(email)

    private data class LoginAttemptState(
        val failureCount: Int,
        val windowStart: Instant,
        val blockedUntil: Instant?,
    ) {
        fun isBlocked(now: Instant): Boolean =
            blockedUntil != null && now.isBefore(blockedUntil)
    }

    companion object {
        private const val PREFIX = "login-attempt:"
    }
}
