package com.blogzip.subscription.repository

import com.blogzip.subscription.domain.Subscription
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionRepository : JpaRepository<Subscription, String> {
    fun findByUserIdAndBlogId(userId: String, blogId: String): Subscription?

    fun findByIdAndUserId(id: String, userId: String): Subscription?
}
