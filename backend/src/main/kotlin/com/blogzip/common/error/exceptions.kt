package com.blogzip.common.error

/** 애플리케이션이 의도적으로 던지는 예외의 최상위. */
abstract class ApplicationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 예상된 실패. 클라이언트에게 알려줘야 하는 "정상적인 실패"다.
 *
 * 새 실패 상황은 [ErrorCode]에 추가한다. 컨트롤러에서 응답 본문을 직접 만들지 않는다.
 * docs/decisions/010-api-response-contract.md
 */
open class BusinessException(
    val errorCode: ErrorCode,
    val detail: Any? = null,
    cause: Throwable? = null,
) : ApplicationException(errorCode.messageKey, cause)
