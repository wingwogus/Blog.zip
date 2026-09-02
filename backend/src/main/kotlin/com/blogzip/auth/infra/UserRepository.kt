package com.blogzip.auth.infra

import com.blogzip.auth.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

/**
 * User 영속 계약. 조회 이메일은 [com.blogzip.auth.domain.Email.normalize]된 값이어야 한다.
 */
interface UserRepository : JpaRepository<User, String> {
    fun findByEmail(email: String): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: String): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean
}
