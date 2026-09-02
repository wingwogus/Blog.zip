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
 * 가입 사용자. 식별자는 ULID이며 이메일은 정규화해 저장한다.
 *
 * [com.blogzip.common.persistence.UlidEntity]와 같은 Persistable 규칙을 따른다.
 * Postgres `char(26)`는 JDBC CHAR(`bpchar`)이므로 ID에 CHAR 매핑을 명시한다.
 * docs/specs/auth.md 7장, docs/decisions/006-id-strategy.md
 */
@Entity
@Table(name = "users")
class User(
    email: String,
    passwordHash: String,
    nickname: String,
    createdAt: Instant = Instant.now(),
) : Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = Ulid.LENGTH)
    private val id: String = Ulid.generate()

    @Column(name = "email", nullable = false, unique = true, length = Email.MAX_LENGTH)
    val email: String = Email.normalize(email)

    @Column(name = "password_hash", nullable = false, length = 72)
    val passwordHash: String = passwordHash

    @Column(name = "nickname", nullable = false, length = 20)
    val nickname: String = nickname.trim()

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "User(id=$id)"
}
