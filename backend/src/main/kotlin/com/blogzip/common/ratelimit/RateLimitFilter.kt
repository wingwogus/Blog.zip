package com.blogzip.common.ratelimit

import com.blogzip.auth.infra.AccessJwtProvider
import com.blogzip.common.error.ErrorCode
import com.blogzip.common.error.ErrorResponseFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

/**
 * Applies the application-wide fixed-window request limits before Spring Security.
 *
 * Authentication is intentionally not read from SecurityContext here. A valid
 * access token is parsed only to classify the request, so unauthenticated
 * requests are limited as well.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RateLimitFilter(
    private val limiter: FixedWindowRateLimiter,
    private val accessJwtProvider: AccessJwtProvider,
    private val errorResponses: ErrorResponseFactory,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val userId = validBearerUserId(request)
        val decision = if (userId != null) {
            limiter.tryAcquire("user:$userId", USER_REQUESTS_PER_MINUTE)
        } else {
            acquireUnauthenticated(request)
        }

        if (!decision.allowed) {
            writeTooManyRequests(response, decision.retryAfterSeconds!!)
            return
        }

        filterChain.doFilter(request, response)
    }

    /** Invalid, expired, malformed, and missing Bearer tokens are unauthenticated. */
    private fun validBearerUserId(request: HttpServletRequest): String? {
        val authorization = request.getHeader(AUTHORIZATION_HEADER) ?: return null
        if (!authorization.regionMatches(0, BEARER_PREFIX, 0, BEARER_PREFIX.length, ignoreCase = true)) {
            return null
        }

        val token = authorization.substring(BEARER_PREFIX.length).trim()
        if (token.isEmpty() || token.any(Char::isWhitespace)) return null

        return try {
            accessJwtProvider.parse(token).userId
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun acquireUnauthenticated(request: HttpServletRequest): RateLimitDecision {
        val ip = remoteAddress(request)
        val general = limiter.tryAcquire("ip:$ip", ANONYMOUS_REQUESTS_PER_MINUTE)
        if (!general.allowed || !authPath(request)) return general
        return limiter.tryAcquire("auth-ip:$ip", AUTH_REQUESTS_PER_MINUTE)
    }

    private fun authPath(request: HttpServletRequest): Boolean {
        val contextPath = request.contextPath.orEmpty()
        val path = request.requestURI.removePrefix(contextPath)
        return path == SIGNUP_PATH || path == LOGIN_PATH
    }

    private fun remoteAddress(request: HttpServletRequest): String =
        request.remoteAddr?.takeIf { it.isNotBlank() } ?: UNKNOWN_ADDRESS

    private fun writeTooManyRequests(response: HttpServletResponse, retryAfterSeconds: Long) {
        val body = errorResponses.build(ErrorCode.TOO_MANY_REQUESTS).body ?: return
        response.status = ErrorCode.TOO_MANY_REQUESTS.status
        response.setHeader(RETRY_AFTER_HEADER, retryAfterSeconds.toString())
        jsonConverter().write(body, MediaType.APPLICATION_JSON, ServletServerHttpResponse(response))
    }

    private fun jsonConverter(): JacksonJsonHttpMessageConverter =
        JacksonJsonHttpMessageConverter(jsonMapper)

    companion object {
        const val ANONYMOUS_REQUESTS_PER_MINUTE = 30
        const val AUTH_REQUESTS_PER_MINUTE = 10
        const val USER_REQUESTS_PER_MINUTE = 120
        const val SIGNUP_PATH = "/api/v1/auth/signup"
        const val LOGIN_PATH = "/api/v1/auth/login"
        const val RETRY_AFTER_HEADER = "Retry-After"

        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val UNKNOWN_ADDRESS = "unknown"
    }
}
