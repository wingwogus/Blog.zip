package com.blogzip.auth.repository

import com.blogzip.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** RefreshToken 영속 계약. 조회는 원문이 아니라 SHA-256 해시로 한다. */
interface RefreshTokenRepository : JpaRepository<RefreshToken, String> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query(
        """
        update RefreshToken token
        set token.revokedAt = :revokedAt
        where token.tokenHash = :tokenHash
          and token.revokedAt is null
          and token.expiresAt > :revokedAt
        """,
    )
    fun revokeActiveByTokenHash(
        @Param("tokenHash") tokenHash: String,
        @Param("revokedAt") revokedAt: Instant,
    ): Int

    fun findAllByUserId(userId: String): List<RefreshToken>
}
