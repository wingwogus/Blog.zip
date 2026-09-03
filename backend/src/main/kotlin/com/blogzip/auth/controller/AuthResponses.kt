package com.blogzip.auth.controller

import com.blogzip.auth.service.AuthenticatedUser
import com.blogzip.common.id.IdPrefix
import java.time.Instant

data class UserResponse(
    val id: String,
    val email: String,
    val nickname: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: AuthenticatedUser): UserResponse = UserResponse(
            id = IdPrefix.USER.encode(user.id),
            email = user.email,
            nickname = user.nickname,
            createdAt = user.createdAt,
        )
    }
}

data class AuthResponse(
    val user: UserResponse,
    val accessToken: String,
)

data class RefreshResponse(
    val accessToken: String,
)
