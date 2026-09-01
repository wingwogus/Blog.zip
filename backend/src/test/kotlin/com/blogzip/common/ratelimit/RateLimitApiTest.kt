package com.blogzip.common.ratelimit

import com.blogzip.common.id.Ulid
import com.blogzip.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class RateLimitApiTest : IntegrationTest() {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var rateLimitFilter: RateLimitFilter

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .addFilters<DefaultMockMvcBuilder>(rateLimitFilter)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `로그인 요청 제한을 넘으면 COMMON_004와 Retry-After를 준다`() {
        val ip = uniqueIp()

        repeat(RateLimitFilter.AUTH_REQUESTS_PER_MINUTE) {
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .with { request ->
                        request.remoteAddr = ip
                        request
                    }
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody()),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
        }

        val exceeded = mockMvc.perform(
            post("/api/v1/auth/login")
                .with { request ->
                    request.remoteAddr = ip
                    request
                }
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()),
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("COMMON_004"))
            .andExpect(header().exists(RateLimitFilter.RETRY_AFTER_HEADER))
            .andReturn()

        val retryAfter = exceeded.response.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)
        assertThat(retryAfter).isNotBlank()
        assertThat(retryAfter!!.toLong()).isBetween(1L, 60L)
    }

    private fun loginBody(): String =
        """{"email":"limit-${Ulid.generate().lowercase()}@example.com","password":"password1234"}"""

    private fun uniqueIp(): String {
        val n = Ulid.generate().hashCode().toUInt()
        return "203.0.${(n % 254u) + 1u}.${((n / 254u) % 254u) + 1u}"
    }
}
