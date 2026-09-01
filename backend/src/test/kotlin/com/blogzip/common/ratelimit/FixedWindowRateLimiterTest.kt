package com.blogzip.common.ratelimit

import com.github.benmanes.caffeine.cache.Caffeine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class FixedWindowRateLimiterTest {

    private val windowStart = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `고정 윈도우 안에서는 제한만큼 허용하고 다음 윈도우에서 다시 허용한다`() {
        val clock = MutableClock(windowStart)
        val limiter = limiter(clock)

        repeat(3) {
            assertThat(limiter.tryAcquire("ip:203.0.113.10", 3).allowed).isTrue()
        }
        assertThat(limiter.tryAcquire("ip:203.0.113.10", 3).allowed).isFalse()

        clock.instant = windowStart.plusSeconds(60)
        assertThat(limiter.tryAcquire("ip:203.0.113.10", 3).allowed).isTrue()
    }

    @Test
    fun `초과 응답의 Retry After는 다음 윈도우까지 올림한 초다`() {
        val clock = MutableClock(windowStart.plusSeconds(30).plusMillis(250))
        val limiter = limiter(clock)

        repeat(2) { limiter.tryAcquire("user:user-1", 2) }
        val decision = limiter.tryAcquire("user:user-1", 2)

        assertThat(decision.allowed).isFalse()
        assertThat(decision.retryAfterSeconds).isEqualTo(30)
    }

    @Test
    fun `서로 다른 키는 독립적으로 센다`() {
        val limiter = limiter(Clock.fixed(windowStart, ZoneOffset.UTC))

        repeat(2) { limiter.tryAcquire("ip:203.0.113.10", 2) }

        assertThat(limiter.tryAcquire("ip:203.0.113.11", 2).allowed).isTrue()
        assertThat(limiter.tryAcquire("user:user-1", 2).allowed).isTrue()
    }

    private fun limiter(clock: Clock): FixedWindowRateLimiter = FixedWindowRateLimiter(
        cache = Caffeine.newBuilder().build(),
        clock = clock,
        window = Duration.ofMinutes(1),
    )

    private class MutableClock(initialInstant: Instant) : Clock() {
        var instant: Instant = initialInstant

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
