package com.blogzip.auth

import com.blogzip.auth.controller.RefreshTokenCookieWriter
import com.blogzip.common.id.IdPrefix
import com.blogzip.common.id.Ulid
import com.blogzip.support.IntegrationTest
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import jakarta.servlet.http.Cookie
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Transactional
class AuthApiTest : IntegrationTest() {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `가입하면 201과 쿠키를 주고 인증 API를 바로 호출할 수 있다`() {
        val email = uniqueEmail()
        val result = mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.user.email").value(email))
            .andExpect(jsonPath("$.data.user.nickname").value("재현"))
            .andExpect(jsonPath("$.data.user.id").value(org.hamcrest.Matchers.startsWith("usr_")))
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andReturn()

        assertThat(refreshCookie(result).value).isNotBlank()
        assertThat(refreshCookie(result).isHttpOnly).isTrue()

        mockMvc.perform(
            get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${accessToken(result)}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.nickname").value("재현"))
    }

    @Test
    fun `같은 이메일은 AUTH_003이다`() {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email)),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email.uppercase())),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("AUTH_003"))
    }

    @Test
    fun `비밀번호가 8자 미만이면 COMMON_001이다`() {
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail()}","password":"short","nickname":"재현"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))
    }

    @Test
    fun `없는 이메일과 틀린 비밀번호는 같은 AUTH_004다`() {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email)),
        ).andExpect(status().isCreated)

        val missing = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nobody-${Ulid.generate()}@example.com","password":"password1234"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_004"))
            .andReturn()

        val wrong = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"wrong-password"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_004"))
            .andReturn()

        assertThat(missing.response.status).isEqualTo(wrong.response.status)
        assertThat(errorCode(missing)).isEqualTo(errorCode(wrong))
    }

    @Test
    fun `연속 5회 실패 후 6번째는 AUTH_006이다`() {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email)),
        ).andExpect(status().isCreated)

        repeat(5) {
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","password":"wrong-password"}"""),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
        }

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"wrong-password"}"""),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("AUTH_006"))
    }

    @Test
    fun `재발급 성공 후 이전 refreshToken은 AUTH_005다`() {
        val signup = signup(uniqueEmail())
        val oldCookie = refreshCookie(signup)

        val refreshed = mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(oldCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isString)
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andReturn()

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(oldCookie))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_005"))

        mockMvc.perform(
            get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${accessToken(refreshed)}"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `무효화된 refreshToken 재사용은 다른 토큰도 무효화한다`() {
        val signup = signup(uniqueEmail())
        val first = refreshCookie(signup)
        val rotated = mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(first))
            .andExpect(status().isOk)
            .andReturn()
        val second = refreshCookie(rotated)

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(first))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_005"))

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(second))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_005"))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `동일 refreshToken의 동시 재발급은 하나만 성공하고 후속 토큰을 모두 무효화한다`() {
        val original = refreshCookie(signup(uniqueEmail()))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(original)).andReturn()
                })
            }
            check(ready.await(5, TimeUnit.SECONDS))
            start.countDown()

            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            val successes = results.filter { it.response.status == 200 }
            assertThat(successes).hasSize(1)
            assertThat(results.filter { it.response.status != 200 }.map(::errorCode))
                .containsOnly("AUTH_005")

            mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie(successes.single())))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_005"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `재사용 탐지와 다른 활성 refreshToken의 동시 사용 뒤에는 활성 토큰이 남지 않는다`() {
        val email = uniqueEmail()
        val original = refreshCookie(signup(email))
        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(original))
            .andExpect(status().isOk)
        val other = refreshCookie(
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","password":"password1234"}"""),
            ).andExpect(status().isOk).andReturn(),
        )

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(original, other).map { cookie ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(cookie)).andReturn()
                })
            }
            check(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }

            results.filter { it.response.status == 200 }.forEach { result ->
                mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie(result)))
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error.code").value("AUTH_005"))
            }
            mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(other))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_005"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `로그아웃 후 재발급은 AUTH_005이고 이미 무효여도 204다`() {
        val signup = signup(uniqueEmail())
        val cookie = refreshCookie(signup)
        val token = accessToken(signup)

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .cookie(cookie),
        ).andExpect(status().isNoContent)

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(cookie))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_005"))

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .cookie(cookie),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `토큰 없이 users me는 AUTH_001이다`() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_001"))
    }

    @Test
    fun `응답에 비밀번호와 refreshToken 원문이 없다`() {
        val password = "password1234"
        val result = mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(uniqueEmail(), password)),
        ).andExpect(status().isCreated).andReturn()

        val body = result.response.contentAsString
        assertThat(body).doesNotContain(password)
        assertThat(body).doesNotContain(refreshCookie(result).value)
        assertThat(IdPrefix.USER.decodeOrNull(JsonPath.read(body, "$.data.user.id"))).isNotNull()
    }

    private fun signup(email: String): MvcResult =
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email)),
        ).andExpect(status().isCreated).andReturn()

    private fun signupBody(email: String, password: String = "password1234"): String =
        """{"email":"$email","password":"$password","nickname":"재현"}"""

    private fun uniqueEmail(): String = "user-${Ulid.generate().lowercase()}@example.com"

    private fun accessToken(result: MvcResult): String =
        JsonPath.read(result.response.contentAsString, "$.data.accessToken")

    private fun errorCode(result: MvcResult): String =
        JsonPath.read(result.response.contentAsString, "$.error.code")

    private fun refreshCookie(result: MvcResult): Cookie =
        result.response.getCookie(RefreshTokenCookieWriter.COOKIE_NAME)
            ?: error("refreshToken 쿠키가 없다")
}
