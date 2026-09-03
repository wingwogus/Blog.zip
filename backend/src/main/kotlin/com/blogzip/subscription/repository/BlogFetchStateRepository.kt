package com.blogzip.subscription.repository

import com.blogzip.subscription.domain.BlogFetchState
import org.springframework.data.jpa.repository.JpaRepository

interface BlogFetchStateRepository : JpaRepository<BlogFetchState, String>
