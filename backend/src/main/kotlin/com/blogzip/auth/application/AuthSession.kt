package com.blogzip.auth.application

import com.blogzip.auth.domain.User
import java.time.Instant

data class AuthenticatedUser(
    val id: String,
    val email: String,
    val nickname: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): AuthenticatedUser = AuthenticatedUser(
            id = user.getId(),
            email = user.email,
            nickname = user.nickname,
            createdAt = user.createdAt,
        )
    }
}

data class AuthSession(
    val user: AuthenticatedUser,
    val accessToken: String,
    val refreshToken: String,
)
