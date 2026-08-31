package com.blogzip.common.error

import com.blogzip.common.web.ApiError
import com.blogzip.common.web.ApiResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

/**
 * [ErrorCode]를 실제 HTTP 응답으로 바꾼다.
 *
 * `messageKey`를 [MessageSource]로 해석해 사람이 읽는 [ApiError.message]를 채운다.
 * 서버가 사용자 노출 문구의 단일 출처가 되도록 한 결정이다.
 * docs/decisions/010-api-response-contract.md
 */
@Component
class ErrorResponseFactory(
    private val messageSource: MessageSource,
) {
    fun build(errorCode: ErrorCode, detail: Any? = null): ResponseEntity<ApiResponse<Unit>> {
        val body = ApiResponse.fail(
            ApiError(
                code = errorCode.code,
                messageKey = errorCode.messageKey,
                message = resolve(errorCode.messageKey),
                detail = detail,
            ),
        )
        return ResponseEntity.status(errorCode.status).body(body)
    }

    private fun resolve(messageKey: String): String =
        messageSource.getMessage(messageKey, null, messageKey, LocaleContextHolder.getLocale())
            ?: messageKey
}
