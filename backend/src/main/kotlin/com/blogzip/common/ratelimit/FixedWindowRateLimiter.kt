package com.blogzip.common.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * A fixed-window counter backed by the application's bounded Caffeine cache.
 *
 * The window start is part of the cache key rather than being inferred from
 * cache expiration. This keeps the algorithm deterministic when [clock] is
 * controlled in tests and prevents a request from extending a window.
 */
@Component
class FixedWindowRateLimiter(
    @param:Qualifier("rateLimitCache")
    private val cache: Cache<String, Any>,
    private val clock: Clock = Clock.systemUTC(),
    private val window: Duration = DEFAULT_WINDOW,
) {

    init {
        require(!window.isZero && !window.isNegative) { "rate-limit window must be positive" }
        require(window.seconds > 0 && window.nano == 0) {
            "rate-limit window must contain a whole number of seconds"
        }
    }

    fun tryAcquire(key: String, limit: Int): RateLimitDecision {
        require(key.isNotBlank()) { "rate-limit key must not be blank" }
        require(limit > 0) { "rate-limit limit must be positive" }

        val now = Instant.now(clock)
        val windowStart = windowStart(now)
        val cacheKey = "$key:${windowStart.epochSecond}"
        val counter = cache.get(cacheKey) { AtomicInteger() } as AtomicInteger
        val count = counter.updateAndGet { current ->
            if (current <= limit) current + 1 else current
        }

        if (count <= limit) {
            return RateLimitDecision(allowed = true, retryAfterSeconds = null)
        }

        return RateLimitDecision(
            allowed = false,
            retryAfterSeconds = retryAfterSeconds(now, windowStart.plus(window)),
        )
    }

    private fun windowStart(now: Instant): Instant {
        val windowSeconds = window.seconds
        val bucket = Math.floorDiv(now.epochSecond, windowSeconds)
        return Instant.ofEpochSecond(bucket * windowSeconds)
    }

    private fun retryAfterSeconds(now: Instant, nextWindow: Instant): Long {
        val remaining = Duration.between(now, nextWindow)
        return remaining.seconds + if (remaining.nano == 0) 0 else 1
    }

    companion object {
        val DEFAULT_WINDOW: Duration = Duration.ofMinutes(1)
    }
}

data class RateLimitDecision(
    val allowed: Boolean,
    val retryAfterSeconds: Long?,
)
