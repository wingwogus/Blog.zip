package com.blogzip.subscription.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * 단기 상태 저장. docs/decisions/009-ephemeral-state.md
 *
 * 이 설정은 애플리케이션 인스턴스가 1대라는 전제에 의존한다.
 * 인스턴스를 늘리면 요청 제한이 인스턴스별로 세어지고 lookupToken이 인스턴스 간에 공유되지 않는다.
 * 증설 작업에는 009 재검토가 반드시 포함된다.
 *
 * 모든 캐시에 만료 시간과 최대 크기를 지정한다. 상한 없는 Map은 OOM으로 이어진다.
 */
@Configuration
class SubscriptionCacheConfig(
    private val properties: EphemeralStateProperties,
) {

    /** Blog 탐색 결과를 생성 요청까지 이어주는 단기 토큰. docs/specs/blog-subscription.md */
    @Bean
    fun blogLookupCache(): Cache<String, Any> = Caffeine.newBuilder()
        .expireAfterWrite(properties.lookupTokenTtl)
        .maximumSize(properties.lookupTokenMaxEntries)
        .build()

    @Bean
    fun blogLookupRateCache(): Cache<String, Any> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(100_000)
        .build()

}

@ConfigurationProperties(prefix = "app.ephemeral")
data class EphemeralStateProperties(
    val lookupTokenTtl: Duration = Duration.ofMinutes(10),
    val lookupTokenMaxEntries: Long = 10_000,
)
