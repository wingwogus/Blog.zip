package com.blogzip.auth.service

import com.blogzip.auth.domain.Email
import com.blogzip.auth.domain.User
import com.blogzip.auth.repository.UserRepository
import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val accessJwtProvider: AccessJwtProvider,
    private val refreshTokenService: RefreshTokenService,
    private val loginAttemptLimiter: LoginAttemptLimiter,
    private val clock: Clock,
) {
    private val dummyPasswordHash: String =
        checkNotNull(passwordEncoder.encode("dummy-password-never-used"))

    @Transactional
    fun signup(email: String, password: String, nickname: String): AuthSession {
        val normalized = Email.normalize(email)
        if (userRepository.existsByEmail(normalized)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }
        val user = User(
            email = normalized,
            passwordHash = checkNotNull(passwordEncoder.encode(password)),
            nickname = nickname.trim(),
            createdAt = Instant.now(clock),
        )
        try {
            userRepository.saveAndFlush(user)
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }
        return sessionFor(user)
    }

    @Transactional
    fun login(email: String, password: String): AuthSession {
        val normalized = Email.normalize(email)
        loginAttemptLimiter.assertNotBlocked(normalized)
        val user = userRepository.findWithLockByEmail(normalized)
        val matches = if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash)
            false
        } else {
            passwordEncoder.matches(password, user.passwordHash)
        }
        if (user == null || !matches) {
            loginAttemptLimiter.recordFailure(normalized)
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }
        loginAttemptLimiter.clear(normalized)
        return sessionFor(user)
    }

    fun refresh(rawRefreshToken: String?): AuthSession {
        if (rawRefreshToken.isNullOrBlank()) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        val consumption = refreshTokenService.consume(rawRefreshToken)
        if (consumption is RefreshTokenConsumption.Reused) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        if (consumption !is RefreshTokenConsumption.Consumed) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        val user = userRepository.findById(consumption.userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        val accessToken = accessJwtProvider.issue(user.getId())
        return AuthSession(AuthenticatedUser.from(user), accessToken, consumption.replacement)
    }

    fun logout(userId: String, rawRefreshToken: String?) {
        refreshTokenService.revokeOwned(userId, rawRefreshToken)
    }

    @Transactional(readOnly = true)
    fun getMe(userId: String): AuthenticatedUser {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        return AuthenticatedUser.from(user)
    }

    private fun sessionFor(user: User): AuthSession {
        val accessToken = accessJwtProvider.issue(user.getId())
        val refreshToken = refreshTokenService.issue(user.getId())
        return AuthSession(AuthenticatedUser.from(user), accessToken, refreshToken)
    }
}
