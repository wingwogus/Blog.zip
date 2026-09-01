package com.blogzip.auth.infra

import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import com.blogzip.common.error.ErrorResponseFactory
import com.blogzip.common.logging.LoggingUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bearer accessToken을 검증해 SecurityContext와 MDC userId를 채운다.
 * 토큰 원문은 로그에 남기지 않는다.
 * docs/decisions/002-auth-strategy.md
 */
class JwtAuthenticationFilter(
    private val accessJwtProvider: AccessJwtProvider,
    private val errorResponses: ErrorResponseFactory,
    private val jsonConverter: HttpMessageConverter<Any>,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath.ifBlank { request.requestURI }
        return path in PUBLIC_AUTH_PATHS
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractBearer(request)
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = try {
            accessJwtProvider.parse(token)
        } catch (e: BusinessException) {
            SecurityContextHolder.clearContext()
            writeError(response, e.errorCode)
            return
        }

        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            jwt.userId,
            null,
            emptyList(),
        )
        SecurityContextHolder.getContext().authentication = authentication
        MDC.put(LoggingUtil.MDC_USER_ID, jwt.userId)
        filterChain.doFilter(request, response)
    }

    private fun extractBearer(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        return header.substring(BEARER_PREFIX.length).trim().takeIf { it.isNotEmpty() }
    }

    private fun writeError(response: HttpServletResponse, errorCode: ErrorCode) {
        val body = errorResponses.build(errorCode).body ?: return
        response.status = errorCode.status
        jsonConverter.write(body, MediaType.APPLICATION_JSON, ServletServerHttpResponse(response))
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private val PUBLIC_AUTH_PATHS = setOf(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/token/refresh",
        )
    }
}
