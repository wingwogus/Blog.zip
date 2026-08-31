package com.blogzip.common.error

import com.blogzip.common.logging.LoggingUtil
import com.blogzip.common.web.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 예외를 Spec에 정의된 에러 응답으로 바꾸는 유일한 지점.
 *
 * 템플릿(springboot-kotlin-initial-template)의 구성을 가져오고
 * ErrorCode 값과 messageKey/message 분리를 Blog.zip 규약에 맞췄다.
 *
 * docs/decisions/010-api-response-contract.md
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val errorResponses: ErrorResponseFactory,
) {
    private val logger = KotlinLogging.logger {}

    /** 예상된 실패. 클라이언트에게 알려줄 정상적인 실패이므로 WARN만 남긴다. */
    @ExceptionHandler
    fun handleBusiness(e: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn { "business: ${e.errorCode.code}" }
        return errorResponses.build(e.errorCode, e.detail)
    }

    /** @RequestBody DTO 검증 실패. */
    @ExceptionHandler
    fun handleMethodArgumentNotValid(
        e: MethodArgumentNotValidException,
    ): ResponseEntity<ApiResponse<Unit>> {
        LoggingUtil.logBusinessError(logger, e)
        val fieldError = e.bindingResult.fieldErrors.firstOrNull()
        return errorResponses.build(
            ErrorCode.INVALID_INPUT,
            fieldError?.let {
                mapOf("field" to it.field, "reason" to (it.defaultMessage ?: "Invalid value"))
            },
        )
    }

    /** @ModelAttribute 바인딩 검증 실패. */
    @ExceptionHandler
    fun handleBind(e: BindException): ResponseEntity<ApiResponse<Unit>> {
        LoggingUtil.logBusinessError(logger, e)
        val fieldError = e.bindingResult.fieldErrors.firstOrNull()
        return errorResponses.build(
            ErrorCode.INVALID_INPUT,
            fieldError?.let {
                mapOf("field" to it.field, "reason" to (it.defaultMessage ?: "Invalid value"))
            },
        )
    }

    @ExceptionHandler
    fun handleConstraintViolation(
        e: ConstraintViolationException,
    ): ResponseEntity<ApiResponse<Unit>> {
        LoggingUtil.logBusinessError(logger, e)
        val violation = e.constraintViolations.firstOrNull()
        return errorResponses.build(
            ErrorCode.INVALID_INPUT,
            violation?.let {
                mapOf(
                    "field" to it.propertyPath.toString().substringAfterLast('.'),
                    "reason" to it.message,
                )
            },
        )
    }

    /** 메서드 파라미터 검증 실패 (Spring 6.1+ @Valid on @RequestParam/@PathVariable). */
    @ExceptionHandler
    fun handleHandlerMethodValidation(
        e: HandlerMethodValidationException,
    ): ResponseEntity<ApiResponse<Unit>> {
        LoggingUtil.logBusinessError(logger, e)
        return errorResponses.build(ErrorCode.INVALID_INPUT)
    }

    /** 쿼리 파라미터, 경로 변수 타입 변환 실패. */
    @ExceptionHandler
    fun handleTypeMismatch(
        e: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ApiResponse<Unit>> {
        LoggingUtil.logBusinessError(logger, e)
        return errorResponses.build(
            ErrorCode.INVALID_INPUT,
            mapOf("field" to e.name, "reason" to "Invalid value"),
        )
    }

    /** 필수 쿼리 파라미터 누락. */
    @ExceptionHandler
    fun handleMissingParameter(
        e: MissingServletRequestParameterException,
    ): ResponseEntity<ApiResponse<Unit>> {
        LoggingUtil.logBusinessError(logger, e)
        return errorResponses.build(
            ErrorCode.INVALID_INPUT,
            mapOf("field" to e.parameterName, "reason" to "Required parameter is missing"),
        )
    }

    /** JSON 파싱 실패. 예외 원문을 클라이언트에 내려보내지 않는다. */
    @ExceptionHandler
    fun handleJsonParse(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Unit>> {
        val eventId = LoggingUtil.logBusinessError(logger, e)
        return errorResponses.build(ErrorCode.INVALID_JSON, mapOf("eventId" to eventId))
    }

    /** 정적 리소스 요청 등 매핑되지 않은 경로. 에러 로그를 남기지 않는다. */
    @ExceptionHandler
    fun handleNoResourceFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<Unit>> =
        errorResponses.build(ErrorCode.RESOURCE_NOT_FOUND)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        val eventId = LoggingUtil.logUnexpectedError(logger, e)
        return errorResponses.build(ErrorCode.INTERNAL_ERROR, mapOf("eventId" to eventId))
    }
}
