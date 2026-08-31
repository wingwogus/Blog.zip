package com.blogzip.common.id

import com.github.f4b6a3.ulid.UlidCreator

/**
 * 식별자 생성. docs/decisions/006-id-strategy.md
 *
 * DB에는 접두사 없는 canonical 26자 ULID만 저장한다.
 * 접두사는 [IdPrefix]를 통해 직렬화 경계에서만 붙이고 뗀다.
 */
object Ulid {
    const val LENGTH = 26

    fun generate(): String = UlidCreator.getMonotonicUlid().toString()
}

/**
 * API에 노출할 때 붙이는 엔티티 접두사.
 *
 * 요청으로 들어온 ID의 접두사가 기대값과 다르면 거부한다.
 * 다른 엔티티의 ID를 잘못된 경로에 넣는 실수를 조기에 잡는다.
 */
enum class IdPrefix(val value: String) {
    USER("usr_"),
    BLOG("blg_"),
    SUBSCRIPTION("sub_"),
    POST("pst_"),
    OWNERSHIP("own_"),
    OWNERSHIP_VERIFICATION("own_ver_"),
    ;

    /** 저장된 26자 ULID를 API 노출 형태로 바꾼다. */
    fun encode(id: String): String = value + id

    /**
     * API로 들어온 값에서 26자 ULID를 꺼낸다.
     * 접두사가 다르거나 길이가 맞지 않으면 null이다. 호출자가 400으로 변환한다.
     */
    fun decodeOrNull(external: String): String? {
        if (!external.startsWith(value)) return null
        val raw = external.removePrefix(value)
        return raw.takeIf { it.length == Ulid.LENGTH }
    }
}
