package com.blogzip.auth.infra

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/**
 * refreshToken 원문을 SHA-256 소문자 hex로 해시한 값을 만든다.
 * DB에는 이 64자 해시만 저장한다. docs/decisions/002-auth-strategy.md
 */
object RefreshTokenHasher {
    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
