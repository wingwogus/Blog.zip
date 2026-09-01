package com.blogzip.auth.infra

import com.blogzip.auth.domain.AccessJwt
import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import com.blogzip.config.AuthProperties
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * HS256 access JWT 발급/검증.
 *
 * 클레임은 `sub`(userId), `iat`, `exp`만 담는다.
 * 시크릿은 [AuthProperties.jwtSecret]이며 기본값을 두지 않는다.
 * docs/decisions/002-auth-strategy.md
 */
class AccessJwtProvider(
    properties: AuthProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val secretKey: SecretKey
    private val accessTokenTtl = properties.accessTokenTtl
    private val parser: JwtParser

    init {
        require(properties.jwtSecret.isNotBlank()) { "JWT_SECRET 환경변수가 필요하다" }
        secretKey = Keys.hmacShaKeyFor(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8))
        parser = Jwts.parser()
            .verifyWith(secretKey)
            .clock { Date.from(Instant.now(clock)) }
            .build()
    }

    fun issue(userId: String): String {
        val now = Instant.now(clock)
        return Jwts.builder()
            .subject(userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    fun parse(token: String): AccessJwt {
        val claims = try {
            parser.parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED, cause = e)
        } catch (e: MalformedJwtException) {
            throw BusinessException(ErrorCode.MALFORMED_JWT, cause = e)
        } catch (e: JwtException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED, cause = e)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.MALFORMED_JWT, cause = e)
        }

        val userId = claims.subject?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.MALFORMED_JWT)
        val issuedAt = claims.issuedAt?.toInstant()
            ?: throw BusinessException(ErrorCode.MALFORMED_JWT)
        val expiresAt = claims.expiration?.toInstant()
            ?: throw BusinessException(ErrorCode.MALFORMED_JWT)
        return AccessJwt(userId = userId, issuedAt = issuedAt, expiresAt = expiresAt)
    }
}
