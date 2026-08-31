package com.blogzip.common.logging

import io.github.oshai.kotlinlogging.KLogger
import org.slf4j.MDC
import java.util.UUID

/**
 * 템플릿(springboot-kotlin-initial-template)에서 가져왔다.
 *
 * 예상된 실패는 WARN, 예상치 못한 예외는 ERROR로 남긴다.
 * 예상치 못한 예외에는 eventId를 발급해 응답 detail과 서버 로그를 대조할 수 있게 한다.
 *
 * 비밀번호, 토큰, 인증 코드, 쿠키 값을 로그에 남기지 않는다.
 * docs/decisions/002-auth-strategy.md
 */
object LoggingUtil {

    const val MDC_TRACE_ID = "traceId"
    const val MDC_EVENT_ID = "eventId"
    const val MDC_USER_ID = "userId"
    const val MDC_CLIENT_IP = "clientIp"

    fun logBusinessError(logger: KLogger, e: Throwable): String {
        val eventId = newEventId()
        MDC.put(MDC_EVENT_ID, eventId)
        logger.warn { "business error: ${e.javaClass.simpleName}: ${e.message}" }
        return eventId
    }

    fun logUnexpectedError(logger: KLogger, e: Throwable): String {
        val eventId = newEventId()
        MDC.put(MDC_EVENT_ID, eventId)
        logger.error(e) { "unexpected error: ${e.javaClass.simpleName}" }
        return eventId
    }

    private fun newEventId(): String = UUID.randomUUID().toString().substring(0, 8)
}
