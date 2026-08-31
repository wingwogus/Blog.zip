package com.blogzip.common.persistence

import com.blogzip.common.id.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable

/**
 * ULID를 애플리케이션에서 할당하는 엔티티의 공통 상위 타입.
 *
 * ID가 저장 전에 이미 존재하므로 Spring Data의 `save()`는 기존 엔티티로 오인해
 * `merge()`를 호출하고, INSERT마다 불필요한 SELECT가 한 번 더 나간다.
 * [Persistable]로 신규 여부를 직접 알려 그것을 막는다.
 *
 * docs/decisions/006-id-strategy.md 5장
 *
 * `data class`를 쓰지 않으며 동등성은 ID로만 판단한다.
 * docs/decisions/007-persistence-stack.md
 */
@MappedSuperclass
abstract class UlidEntity(
    @Id
    @Column(name = "id", columnDefinition = "char(26)", nullable = false, updatable = false)
    private val id: String = Ulid.generate(),
) : Persistable<String> {

    @Transient
    private var isNewEntity: Boolean = true

    override fun getId(): String = id

    override fun isNew(): Boolean = isNewEntity

    /** JPA가 엔티티를 적재하거나 저장한 뒤 신규 플래그를 내린다. */
    @PostPersist
    @PostLoad
    protected fun markNotNew() {
        isNewEntity = false
    }

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UlidEntity) return false
        if (javaClass != other.javaClass) return false
        return id == other.id
    }

    final override fun hashCode(): Int = id.hashCode()

    final override fun toString(): String = "${javaClass.simpleName}(id=$id)"
}
