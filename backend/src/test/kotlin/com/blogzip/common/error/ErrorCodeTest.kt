package com.blogzip.common.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * 에러 코드 규약 검증. docs/decisions/010-api-response-contract.md
 *
 * 문구 자체를 테스트로 고정하지 않는다. 검증 대상은 기계가 소비하는 값과 제품 원칙 위반이다.
 */
class ErrorCodeTest {

    private val messages: Properties = Properties().apply {
        ErrorCodeTest::class.java.getResourceAsStream("/messages.properties")!!
            .reader(Charsets.UTF_8)
            .use { load(it) }
    }

    @Test
    fun `code는 DOMAIN_NNN 형식이다`() {
        val pattern = Regex("^[A-Z]+_\\d{3}$")
        val violations = ErrorCode.entries.filterNot { pattern.matches(it.code) }
        assertThat(violations).isEmpty()
    }

    @Test
    fun `code는 중복되지 않는다`() {
        val duplicates = ErrorCode.entries
            .groupBy { it.code }
            .filterValues { it.size > 1 }
        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `messageKey는 중복되지 않는다`() {
        val duplicates = ErrorCode.entries
            .groupBy { it.messageKey }
            .filterValues { it.size > 1 }
        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `status는 4xx 또는 5xx다`() {
        assertThat(ErrorCode.entries).allSatisfy {
            assertThat(it.status).isBetween(400, 599)
        }
    }

    @Test
    fun `모든 ErrorCode의 messageKey에 대응하는 문구가 있다`() {
        val missing = ErrorCode.entries
            .map { it.messageKey }
            .filter { messages.getProperty(it).isNullOrBlank() }
        assertThat(missing).isEmpty()
    }

    /**
     * PRD P-002. 사용자 경험에 RSS/Atom 같은 기술 용어를 노출하지 않는다.
     * 문구 표현이 아니라 제품 원칙 위반 여부를 보는 검증이다.
     */
    @Test
    fun `사용자 노출 문구에 기술 용어가 없다`() {
        val forbidden = listOf("RSS", "Atom", "atom", "feed", "Feed", "피드 주소", "XML")
        val violations = messages.stringPropertyNames()
            .filter { key -> forbidden.any { messages.getProperty(key).contains(it) } }
        assertThat(violations).isEmpty()
    }
}
