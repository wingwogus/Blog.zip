package com.blogzip.auth.infra

import com.blogzip.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

/** RefreshToken 영속 계약. 조회는 원문이 아니라 SHA-256 해시로 한다. */
interface RefreshTokenRepository : JpaRepository<RefreshToken, String> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    fun findAllByUserId(userId: String): List<RefreshToken>
}
