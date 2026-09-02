package com.blogzip.auth.api

import com.blogzip.auth.application.AuthenticatedUser
import com.blogzip.auth.application.AuthService
import com.blogzip.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val cookies: RefreshTokenCookieWriter,
) {

    @Operation(summary = "회원가입", description = "계정 생성 및 자동 로그인. 에러: COMMON_001, AUTH_003")
    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val session = authService.signup(request.email, request.password, request.nickname)
        cookies.set(response, session.refreshToken)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(toAuthResponse(session.user, session.accessToken)))
    }

    @Operation(summary = "로그인", description = "에러: COMMON_001, AUTH_004, AUTH_006")
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): ApiResponse<AuthResponse> {
        val session = authService.login(request.email, request.password)
        cookies.set(response, session.refreshToken)
        return ApiResponse.ok(toAuthResponse(session.user, session.accessToken))
    }

    @Operation(summary = "토큰 재발급", description = "에러: AUTH_005")
    @PostMapping("/token/refresh")
    fun refresh(
        @CookieValue(name = RefreshTokenCookieWriter.COOKIE_NAME, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<RefreshResponse> {
        val session = authService.refresh(refreshToken)
        cookies.set(response, session.refreshToken)
        return ApiResponse.ok(RefreshResponse(session.accessToken))
    }

    @Operation(summary = "로그아웃", description = "refreshToken 무효화. 이미 무효해도 204")
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal userId: String,
        @CookieValue(name = RefreshTokenCookieWriter.COOKIE_NAME, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        authService.logout(userId, refreshToken)
        cookies.clear(response)
        return ResponseEntity.noContent().build()
    }

    private fun toAuthResponse(
        user: AuthenticatedUser,
        accessToken: String,
    ): AuthResponse = AuthResponse(user = UserResponse.from(user), accessToken = accessToken)
}
