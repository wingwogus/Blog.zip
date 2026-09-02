package com.blogzip.auth

import com.blogzip.auth.application.AuthService
import com.blogzip.auth.application.LoginAttemptLimiter
import com.blogzip.auth.application.RefreshTokenService
import com.blogzip.auth.domain.RefreshToken
import com.blogzip.auth.domain.User
import com.blogzip.auth.infra.AccessJwtProvider
import com.blogzip.auth.infra.RefreshTokenHasher
import com.blogzip.auth.infra.RefreshTokenRepository
import com.blogzip.auth.infra.UserRepository
import com.blogzip.common.error.BusinessException
import com.blogzip.common.error.ErrorCode
import com.blogzip.config.AuthProperties
import com.github.benmanes.caffeine.cache.Caffeine
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class AuthServiceTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = AuthProperties(
        jwtSecret = "test-only-secret-not-used-in-any-real-environment-0123456789",
        accessTokenTtl = Duration.ofMinutes(30),
        refreshTokenTtl = Duration.ofDays(14),
        loginMaxAttempts = 5,
        loginBlockDuration = Duration.ofMinutes(10),
    )
    private val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder(4)
    private val usersByEmail = ConcurrentHashMap<String, User>()
    private val usersById = ConcurrentHashMap<String, User>()
    private val tokensByHash = ConcurrentHashMap<String, RefreshToken>()
    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var service: AuthService

    @BeforeEach
    fun setUp() {
        usersByEmail.clear()
        usersById.clear()
        tokensByHash.clear()
        userRepository = Mockito.mock(UserRepository::class.java)
        refreshTokenRepository = Mockito.mock(RefreshTokenRepository::class.java)

        Mockito.`when`(userRepository.existsByEmail(Mockito.anyString())).thenAnswer { inv ->
            usersByEmail.containsKey(inv.getArgument(0))
        }
        Mockito.`when`(userRepository.findByEmail(Mockito.anyString())).thenAnswer { inv ->
            usersByEmail[inv.getArgument(0)]
        }
        Mockito.`when`(userRepository.findWithLockByEmail(Mockito.anyString())).thenAnswer { inv ->
            usersByEmail[inv.getArgument(0)]
        }
        Mockito.`when`(userRepository.findById(Mockito.anyString())).thenAnswer { inv ->
            Optional.ofNullable(usersById[inv.getArgument(0)])
        }
        Mockito.`when`(userRepository.findWithLockById(Mockito.anyString())).thenAnswer { inv ->
            usersById[inv.getArgument(0)]
        }
        Mockito.`when`(userRepository.saveAndFlush(Mockito.any(User::class.java))).thenAnswer { inv ->
            storeUser(inv.getArgument(0))
        }
        Mockito.`when`(userRepository.save(Mockito.any(User::class.java))).thenAnswer { inv ->
            storeUser(inv.getArgument(0))
        }

        Mockito.`when`(refreshTokenRepository.findByTokenHash(Mockito.anyString())).thenAnswer { inv ->
            tokensByHash[inv.getArgument(0)]
        }
        Mockito.`when`(
            refreshTokenRepository.revokeActiveByTokenHash(
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH,
            ),
        ).thenAnswer { inv ->
            val token = tokensByHash[inv.getArgument<String>(0)] ?: return@thenAnswer 0
            if (!token.isActive(clock.instant())) return@thenAnswer 0
            token.revoke(inv.getArgument(1))
            1
        }
        Mockito.`when`(refreshTokenRepository.findAllByUserId(Mockito.anyString())).thenAnswer { inv ->
            val userId = inv.getArgument<String>(0)
            tokensByHash.values.filter { it.userId == userId }
        }
        Mockito.`when`(refreshTokenRepository.save(Mockito.any(RefreshToken::class.java))).thenAnswer { inv ->
            storeToken(inv.getArgument(0))
        }
        Mockito.`when`(refreshTokenRepository.saveAll(Mockito.anyList())).thenAnswer { inv ->
            val tokens = inv.getArgument<Iterable<RefreshToken>>(0)
            tokens.forEach { storeToken(it) }
            tokens.toList()
        }

        service = AuthService(
            userRepository,
            passwordEncoder,
            AccessJwtProvider(properties, clock),
            RefreshTokenService(refreshTokenRepository, userRepository, properties, clock),
            LoginAttemptLimiter(
                Caffeine.newBuilder().maximumSize(100).build(),
                properties,
                clock,
            ),
            clock,
        )
    }

    @Test
    fun `가입하면 토큰을 발급하고 비밀번호는 해시로 저장한다`() {
        val session = service.signup("User@Example.COM", "password1234", " 재현 ")

        assertThat(session.user.email).isEqualTo("user@example.com")
        assertThat(session.user.nickname).isEqualTo("재현")
        assertThat(session.accessToken).isNotBlank()
        assertThat(session.refreshToken).isNotBlank()
        val stored = usersByEmail.getValue("user@example.com")
        assertThat(stored.passwordHash).isNotEqualTo("password1234")
        assertThat(passwordEncoder.matches("password1234", stored.passwordHash)).isTrue()
        assertThat(AccessJwtProvider(properties, clock).parse(session.accessToken).userId)
            .isEqualTo(stored.getId())
    }

    @Test
    fun `같은 이메일은 AUTH_003이다`() {
        service.signup("user@example.com", "password1234", "재현")

        assertThatThrownBy { service.signup("USER@example.com", "password1234", "다른") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.DUPLICATE_EMAIL)
    }

    @Test
    fun `없는 이메일과 틀린 비밀번호는 같은 AUTH_004다`() {
        service.signup("user@example.com", "password1234", "재현")

        val missing = catchAuth { service.login("nobody@example.com", "password1234") }
        val wrong = catchAuth { service.login("user@example.com", "wrong-password") }

        assertThat(missing.errorCode).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
        assertThat(wrong.errorCode).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `로그인 성공은 실패 카운터를 지운다`() {
        service.signup("user@example.com", "password1234", "재현")
        repeat(4) { runCatching { service.login("user@example.com", "wrong") } }

        assertThat(service.login("user@example.com", "password1234").accessToken).isNotBlank()
        runCatching { service.login("user@example.com", "wrong") }
        assertThatCode { service.login("user@example.com", "password1234") }
            .doesNotThrowAnyException()
    }

    @Test
    fun `재발급은 refreshToken을 회전한다`() {
        val first = service.signup("user@example.com", "password1234", "재현")
        val second = service.refresh(first.refreshToken)

        assertThat(second.refreshToken).isNotEqualTo(first.refreshToken)
        assertThatThrownBy { service.refresh(first.refreshToken) }
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
    }

    @Test
    fun `무효화된 refreshToken 재사용은 사용자의 모든 토큰을 무효화한다`() {
        val first = service.signup("user@example.com", "password1234", "재현")
        val second = service.refresh(first.refreshToken)

        assertThatThrownBy { service.refresh(first.refreshToken) }
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
        assertThatThrownBy { service.refresh(second.refreshToken) }
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
        assertThat(tokensByHash.values).allMatch { it.isRevoked() }
    }

    @Test
    fun `로그아웃은 해당 refreshToken만 무효화하고 이미 무효여도 예외가 없다`() {
        val session = service.signup("user@example.com", "password1234", "재현")
        val userId = usersByEmail.getValue("user@example.com").getId()

        service.logout(userId, session.refreshToken)
        service.logout(userId, session.refreshToken)
        service.logout(userId, "unknown-token")

        assertThatThrownBy { service.refresh(session.refreshToken) }
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
        assertThat(tokensByHash.getValue(RefreshTokenHasher.hash(session.refreshToken)).isRevoked())
            .isTrue()
    }

    @Test
    fun `만료된 refreshToken은 AUTH_005이고 재사용 감지는 하지 않는다`() {
        val session = service.signup("user@example.com", "password1234", "재현")
        val stored = tokensByHash.getValue(RefreshTokenHasher.hash(session.refreshToken))
        expire(stored, now.minusSeconds(1))

        assertThatThrownBy { service.refresh(session.refreshToken) }
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN)
        assertThat(stored.isRevoked()).isFalse()
    }

    @Test
    fun `내 계정 조회는 저장된 사용자를 반환한다`() {
        val session = service.signup("user@example.com", "password1234", "재현")
        val me = service.getMe(session.user.id)
        assertThat(me.email).isEqualTo("user@example.com")
        assertThat(me.nickname).isEqualTo("재현")
    }

    private fun storeUser(user: User): User {
        usersByEmail[user.email] = user
        usersById[user.getId()] = user
        return user
    }

    private fun storeToken(token: RefreshToken): RefreshToken {
        tokensByHash[token.tokenHash] = token
        return token
    }

    private fun catchAuth(block: () -> Unit): BusinessException =
        org.assertj.core.api.Assertions.catchThrowable(block) as BusinessException

    private fun expire(token: RefreshToken, at: Instant) {
        val field = RefreshToken::class.java.getDeclaredField("expiresAt")
        field.isAccessible = true
        field.set(token, at)
    }
}
