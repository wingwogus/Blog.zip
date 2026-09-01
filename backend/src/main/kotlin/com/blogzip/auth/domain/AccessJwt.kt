package com.blogzip.auth.domain

import java.time.Instant

/**
 * 검증된 access JWT.
 *
 * 클레임은 `sub`(userId), `iat`, `exp`만 가진다.
 * docs/decisions/002-auth-strategy.md
 */
data class AccessJwt(
    val userId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
