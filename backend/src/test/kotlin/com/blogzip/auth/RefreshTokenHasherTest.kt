package com.blogzip.auth

import com.blogzip.auth.domain.RefreshToken
import com.blogzip.auth.service.RefreshTokenHasher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class RefreshTokenHasherTest {

    @Test
    fun `SHA-256 소문자 hex 64자를 반환한다`() {
        val hash = RefreshTokenHasher.hash("refresh-token-raw")

        val expected = MessageDigest.getInstance("SHA-256")
            .digest("refresh-token-raw".toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertThat(hash).isEqualTo(expected)
        assertThat(hash).hasSize(RefreshToken.TOKEN_HASH_LENGTH)
        assertThat(hash).isEqualTo(hash.lowercase())
        assertThat(hash).matches("[0-9a-f]+")
    }

    @Test
    fun `같은 원문은 같은 해시다`() {
        assertThat(RefreshTokenHasher.hash("same"))
            .isEqualTo(RefreshTokenHasher.hash("same"))
    }

    @Test
    fun `다른 원문은 다른 해시다`() {
        assertThat(RefreshTokenHasher.hash("one"))
            .isNotEqualTo(RefreshTokenHasher.hash("two"))
    }
}
