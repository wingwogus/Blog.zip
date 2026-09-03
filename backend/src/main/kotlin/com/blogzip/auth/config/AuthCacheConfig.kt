package com.blogzip.auth.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthCacheConfig(
    private val properties: AuthProperties,
) {

    /** 로그인 실패 카운터와 요청 제한 카운터. docs/decisions/002-auth-strategy.md */
    @Bean
    fun rateLimitCache(): Cache<String, Any> = Caffeine.newBuilder()
        .expireAfterWrite(properties.loginBlockDuration)
        .maximumSize(RATE_LIMIT_CACHE_MAX_ENTRIES)
        .build()

    private companion object {
        const val RATE_LIMIT_CACHE_MAX_ENTRIES = 100_000L
    }
}
