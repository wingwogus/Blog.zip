package com.blogzip.subscription.repository

import com.blogzip.subscription.domain.Blog
import org.springframework.data.jpa.repository.JpaRepository

interface BlogRepository : JpaRepository<Blog, String> {
    fun findByCanonicalUrl(url: String): Blog?
}
