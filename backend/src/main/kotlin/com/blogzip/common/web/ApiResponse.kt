package com.blogzip.common.web

/**
 * 모든 API 응답의 공통 래퍼.
 *
 * 각 Spec 8장의 Response 예시는 [data]에 들어가는 내용을 보여준 것이다.
 * `204 No Content`는 본문이 없으므로 이 래퍼를 쓰지 않는다.
 *
 * docs/decisions/010-api-response-contract.md
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun ok(): ApiResponse<Unit> = ApiResponse(success = true)

        fun fail(error: ApiError): ApiResponse<Unit> = ApiResponse(success = false, error = error)
    }
}

/**
 * [code]는 기계가 판단하는 값이다. 클라이언트 분기는 이 값으로만 한다.
 *
 * [messageKey]는 안정적인 메시지 키, [message]는 사람이 읽는 문장이다.
 * [message] 문구는 예고 없이 바뀔 수 있으므로 분기나 테스트에 쓰지 않는다.
 */
data class ApiError(
    val code: String,
    val messageKey: String,
    val message: String,
    val detail: Any? = null,
)

/**
 * 커서 기반 목록 응답. docs/specs/README.md 공통 API 규약.
 *
 * [nextCursor]가 null이면 마지막 페이지다. 커서 값은 클라이언트가 해석하지 않는다.
 */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
)
