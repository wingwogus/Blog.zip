package com.blogzip.auth.domain

import java.util.Locale

/**
 * 로그인 식별자로 쓰는 이메일.
 *
 * 대소문자를 구분하지 않으므로 저장과 비교 전에 정규화한다.
 * docs/specs/auth.md FR-001, AUTH-BR-001
 */
object Email {
    const val MAX_LENGTH = 254

    fun normalize(raw: String): String = raw.trim().lowercase(Locale.ROOT)
}
