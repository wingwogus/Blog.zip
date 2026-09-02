package com.blogzip.auth.application

import com.blogzip.auth.domain.RefreshToken
import com.blogzip.auth.infra.RefreshTokenHasher
import com.blogzip.auth.infra.RefreshTokenRepository
import com.blogzip.auth.infra.UserRepository
import com.blogzip.config.AuthProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.HexFormat

/**
 * refreshToken 발급, 회전, 무효화.
 *
 * 원문은 반환만 하고 DB에는 SHA-256 해시만 저장한다.
 * 재사용 감지 후 전체 무효화는 호출자가 예외를 던지기 전에 이 트랜잭션이 커밋돼야 한다.
 * docs/decisions/002-auth-strategy.md
 */
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(userId: String): String {
        userRepository.findWithLockById(userId) ?: error("사용자가 없다")
        return persistNew(userId, Instant.now(clock))
    }

    @Transactional
    fun consume(rawToken: String): RefreshTokenConsumption {
        val now = Instant.now(clock)
        val tokenHash = RefreshTokenHasher.hash(rawToken)
        val userId = refreshTokenRepository.findByTokenHash(tokenHash)?.userId
            ?: return RefreshTokenConsumption.Invalid
        userRepository.findWithLockById(userId) ?: return RefreshTokenConsumption.Invalid
        val current = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: return RefreshTokenConsumption.Invalid
        if (current.isExpired(now)) {
            return RefreshTokenConsumption.Invalid
        }
        if (refreshTokenRepository.revokeActiveByTokenHash(tokenHash, now) == 0) {
            revokeAll(userId, now)
            return RefreshTokenConsumption.Reused(userId)
        }
        return RefreshTokenConsumption.Consumed(userId, persistNew(userId, now))
    }

    @Transactional
    fun revokeOwned(userId: String, rawToken: String?) {
        if (rawToken.isNullOrBlank()) return
        val token = refreshTokenRepository.findByTokenHash(RefreshTokenHasher.hash(rawToken)) ?: return
        if (token.userId != userId) return
        token.revoke(Instant.now(clock))
        refreshTokenRepository.save(token)
    }

    fun revokeAll(userId: String) {
        revokeAll(userId, Instant.now(clock))
    }

    private fun revokeAll(userId: String, now: Instant) {
        val tokens = refreshTokenRepository.findAllByUserId(userId)
        tokens.forEach { it.revoke(now) }
        refreshTokenRepository.saveAll(tokens)
    }

    private fun persistNew(userId: String, now: Instant): String {
        val raw = newRawToken()
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = RefreshTokenHasher.hash(raw),
                expiresAt = now.plus(properties.refreshTokenTtl),
                createdAt = now,
            ),
        )
        return raw
    }

    private fun newRawToken(): String {
        val bytes = ByteArray(RAW_TOKEN_BYTES)
        random.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    companion object {
        private const val RAW_TOKEN_BYTES = 32
    }
}

sealed interface RefreshTokenConsumption {
    data class Consumed(val userId: String, val replacement: String) : RefreshTokenConsumption

    data class Reused(val userId: String) : RefreshTokenConsumption

    data object Invalid : RefreshTokenConsumption
}
