package com.blogzip.subscription

import com.blogzip.common.id.IdPrefix
import com.blogzip.common.id.Ulid
import com.blogzip.support.IntegrationTest
import com.jayway.jsonpath.JsonPath
import com.github.benmanes.caffeine.cache.Cache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class SubscriptionPersistenceApiTest : IntegrationTest() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext
    @Autowired
    @Qualifier("blogLookupCache")
    private lateinit var lookupCache: Cache<String, Any>
    private lateinit var mockMvc: MockMvc

    @BeforeEach fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        lookupCache.invalidateAll()
    }

    @Test fun `lookup token ownership and missing mapping`() {
        val owner = signup(); val other = signup(); val token = putLookup(owner)
        mockMvc.perform(createRequest(other, token, "x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.error.code").value("BLOG_005"))
        mockMvc.perform(createRequest(owner, "lkp-missing", "x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.error.code").value("BLOG_005"))
    }

    @Test fun `creation response is safe and duplicate is mapped`() {
        val owner = signup(); val result = mockMvc.perform(createRequest(owner, putLookup(owner), "My blog")).andExpect(status().isCreated).andExpect(jsonPath("$.data.blog.feedUrl").doesNotExist()).andReturn()
        val body = result.response.contentAsString
        assertThat(IdPrefix.SUBSCRIPTION.decodeOrNull(JsonPath.read(body, "$.data.id"))).isNotNull()
        assertThat(IdPrefix.BLOG.decodeOrNull(JsonPath.read(body, "$.data.blog.id"))).isNotNull()
        mockMvc.perform(createRequest(owner, putLookup(owner), "Again")).andExpect(status().isConflict).andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_002"))
    }

    @Test fun `shared blog has independent labels`() {
        val first = signup(); val second = signup()
        val a = mockMvc.perform(createRequest(first, putLookup(first), "Alice")).andExpect(status().isCreated).andReturn()
        val blogId = JsonPath.read<String>(a.response.contentAsString, "$.data.blog.id")
        val b = mockMvc.perform(createRequest(second, putLookup(second), "Bob")).andExpect(status().isCreated).andReturn()
        assertThat(JsonPath.read<String>(b.response.contentAsString, "$.data.friendName")).isEqualTo("Bob")
        assertThat(JsonPath.read<String>(b.response.contentAsString, "$.data.blog.id")).isEqualTo(blogId)
    }

    @Test fun `deletion is owner scoped`() {
        val owner = signup(); val other = signup(); val created = mockMvc.perform(createRequest(owner, putLookup(owner), "Owned")).andExpect(status().isCreated).andReturn()
        val id = JsonPath.read<String>(created.response.contentAsString, "$.data.id")
        mockMvc.perform(delete("/api/v1/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken)).andExpect(status().isNotFound).andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_001"))
        mockMvc.perform(delete("/api/v1/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken)).andExpect(status().isNoContent)
    }

    private data class Auth(val accessToken: String, val userId: String)
    private fun signup(): Auth {
        val email = "user-${Ulid.generate().lowercase()}@example.com"
        val r = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"$email\",\"password\":\"password1234\",\"nickname\":\"Tester\"}" )).andExpect(status().isCreated).andReturn()
        val b = r.response.contentAsString
        val externalUserId = JsonPath.read<String>(b, "$.data.user.id")
        return Auth(
            JsonPath.read(b, "$.data.accessToken"),
            requireNotNull(IdPrefix.USER.decodeOrNull(externalUserId)),
        )
    }
    private fun putLookup(auth: Auth): String {
        val token = "lkp-test-${Ulid.generate()}"; val c = Class.forName("com.blogzip.subscription.service.LookupEntry").declaredConstructors.single().apply { isAccessible = true }
        lookupCache.put(token, c.newInstance(auth.userId, "https://shared.example/", "https://shared.example/", "https://shared.example/feed.xml", "Shared", "GENERIC") as Any); return token
    }
    private fun createRequest(auth: Auth, token: String, name: String) = post("/api/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.accessToken).contentType(MediaType.APPLICATION_JSON).content("{\"lookupToken\":\"$token\",\"friendName\":\"$name\"}")
}
