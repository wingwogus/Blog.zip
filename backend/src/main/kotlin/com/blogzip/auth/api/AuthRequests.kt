package com.blogzip.auth.api

import com.blogzip.auth.domain.Email
import jakarta.validation.constraints.Email as EmailFormat
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank
    @field:EmailFormat
    @field:Size(max = Email.MAX_LENGTH)
    val email: String,

    @field:NotBlank
    @field:Size(min = PASSWORD_MIN, max = PASSWORD_MAX)
    val password: String,

    @field:NotBlank
    @field:Size(min = NICKNAME_MIN, max = NICKNAME_MAX)
    val nickname: String,
) {
    override fun toString(): String = "SignupRequest(email=$email, nickname=$nickname)"

    companion object {
        const val PASSWORD_MIN = 8
        const val PASSWORD_MAX = 64
        const val NICKNAME_MIN = 1
        const val NICKNAME_MAX = 20
    }
}

data class LoginRequest(
    @field:NotBlank
    @field:EmailFormat
    @field:Size(max = Email.MAX_LENGTH)
    val email: String,

    @field:NotBlank
    val password: String,
) {
    override fun toString(): String = "LoginRequest(email=$email)"
}
