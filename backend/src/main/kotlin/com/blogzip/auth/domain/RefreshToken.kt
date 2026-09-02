package com.blogzip.auth.domain

import com.blogzip.common.id.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
import java.time.Instant

/**
 * accessToken 재발급용 refreshToken.
 *
 * 원문이 아니라 SHA-256 해시를 저장한다.
 * [com.blogzip.common.persistence.UlidEntity]와 같은 Persistable 규칙을 따른다.
 * docs/decisions/002-auth-strategy.md, docs/specs/auth.md FR-003
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    userId: String,
    tokenHash: String,
    expiresAt: Instant,
    createdAt: Instant = Instant.now(),
) : Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = Ulid.LENGTH)
    private val id: String = Ulid.generate()

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", nullable = false, updatable = false, length = Ulid.LENGTH)
    val userId: String = userId

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "token_hash",
        nullable = false,
        unique = true,
        updatable = false,
        length = TOKEN_HASH_LENGTH,
    )
    val tokenHash: String = tokenHash

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = expiresAt

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = createdAt

    @Transient
    private var isNewEntity: Boolean = true

    override fun getId(): String = id

    override fun isNew(): Boolean = isNewEntity

    @PostPersist
    @PostLoad
    protected fun markNotNew() {
        isNewEntity = false
    }

    fun isRevoked(): Boolean = revokedAt != null

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    fun isActive(now: Instant): Boolean = !isRevoked() && !isExpired(now)

    /** 이미 무효면 시각을 덮어쓰지 않는다. */
    fun revoke(at: Instant) {
        if (revokedAt == null) {
            revokedAt = at
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefreshToken) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "RefreshToken(id=$id)"

    companion object {
        const val TOKEN_HASH_LENGTH = 64
    }
}
