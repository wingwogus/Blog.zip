package com.blogzip.auth.controller

import com.blogzip.auth.service.AuthService
import com.blogzip.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User")
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val authService: AuthService,
) {

    @Operation(summary = "내 계정 조회", description = "에러: AUTH_001, USER_001")
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: String): ApiResponse<UserResponse> =
        ApiResponse.ok(UserResponse.from(authService.getMe(userId)))
}
