package com.blogzip.common.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** docs/decisions/006-id-strategy.md */
class IdPrefixTest {

    @Test
    fun `생성된 ULID는 26자다`() {
        assertThat(Ulid.generate()).hasSize(Ulid.LENGTH)
    }

    @Test
    fun `ULID는 생성 순서대로 사전순 정렬된다`() {
        // Feed 커서 페이지네이션이 문자열 비교로 tie-breaker를 성립시키는 전제다.
        val ids = (1..200).map { Ulid.generate() }
        assertThat(ids).isSorted
    }

    @Test
    fun `encode는 접두사를 붙이고 decode는 원본을 돌려준다`() {
        val raw = Ulid.generate()
        val external = IdPrefix.SUBSCRIPTION.encode(raw)

        assertThat(external).startsWith("sub_")
        assertThat(IdPrefix.SUBSCRIPTION.decodeOrNull(external)).isEqualTo(raw)
    }

    @Test
    fun `다른 엔티티의 접두사는 거부한다`() {
        val external = IdPrefix.POST.encode(Ulid.generate())

        assertThat(IdPrefix.SUBSCRIPTION.decodeOrNull(external)).isNull()
    }

    @Test
    fun `길이가 맞지 않으면 거부한다`() {
        assertThat(IdPrefix.USER.decodeOrNull("usr_TOOSHORT")).isNull()
        assertThat(IdPrefix.USER.decodeOrNull("usr_")).isNull()
    }

    @Test
    fun `접두사가 없으면 거부한다`() {
        assertThat(IdPrefix.USER.decodeOrNull(Ulid.generate())).isNull()
    }

    @Test
    fun `own_ver_는 own_보다 먼저 판별되어야 구분된다`() {
        // own_ver_ 접두사가 own_로 시작하므로 순서를 잘못 다루면 섞인다.
        val verification = IdPrefix.OWNERSHIP_VERIFICATION.encode(Ulid.generate())

        assertThat(IdPrefix.OWNERSHIP_VERIFICATION.decodeOrNull(verification)).isNotNull()
        // own_ 로 decode하면 남은 문자열 길이가 26이 아니라 거부된다.
        assertThat(IdPrefix.OWNERSHIP.decodeOrNull(verification)).isNull()
    }
}
