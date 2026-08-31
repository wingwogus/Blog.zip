package com.blogzip.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청마다 traceId와 clientIp를 MDC에 넣는다.
 * 템플릿(springboot-kotlin-initial-template)에서 가져왔다.
 *
 * clientIp는 신뢰할 프록시를 설정한 뒤에만 X-Forwarded-For를 읽어야 한다.
 * 지금은 신뢰 설정이 없으므로 원격 주소만 쓴다.
 * docs/decisions/002-auth-strategy.md
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            MDC.put(LoggingUtil.MDC_TRACE_ID, resolveTraceId(request))
            MDC.put(LoggingUtil.MDC_CLIENT_IP, request.remoteAddr ?: "unknown")
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }

    private fun resolveTraceId(request: HttpServletRequest): String =
        request.getHeader(HEADER_TRACE_ID)?.takeIf { it.isNotBlank() && it.length <= MAX_TRACE_ID_LENGTH }
            ?: UUID.randomUUID().toString().substring(0, 8)

    companion object {
        private const val HEADER_TRACE_ID = "X-Request-Id"
        private const val MAX_TRACE_ID_LENGTH = 64
    }
}
