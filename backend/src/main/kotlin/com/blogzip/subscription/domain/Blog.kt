package com.blogzip.subscription.domain

import com.blogzip.common.id.Ulid
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
import java.time.Instant

@Entity
@Table(name="blogs")
class Blog(
    canonicalUrl:String, siteUrl:String, feedUrl:String, title:String, platform:String,
    createdAt:Instant=Instant.now()
): Persistable<String> {
 @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=26,updatable=false) private val id:String=Ulid.generate()
 @Column(name="canonical_url",nullable=false,unique=true,length=2048) val canonicalUrl=canonicalUrl
 @Column(name="site_url",nullable=false,length=2048) val siteUrl=siteUrl
 @Column(name="feed_url",nullable=false,length=2048) val feedUrl=feedUrl
 @Column(nullable=false,length=255) val title=title
 @Column(nullable=false,length=20) val platform=platform
 @JdbcTypeCode(SqlTypes.CHAR) @Column(name="owner_user_id", length=26) val ownerUserId:String?=null
 @Column(name="created_at",updatable=false) val createdAt=createdAt
 @Transient private var isNew=true
 override fun getId()=id
 override fun isNew()=isNew
 @PostPersist @PostLoad fun mark(){isNew=false}
}

@Entity
@Table(name="subscriptions", uniqueConstraints=[UniqueConstraint(columnNames=["user_id","blog_id"])])
class Subscription(userId:String, blogId:String, friendName:String, createdAt:Instant=Instant.now()):Persistable<String>{
 @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=26,updatable=false) private val id:String=Ulid.generate()
 @JdbcTypeCode(SqlTypes.CHAR) @Column(name="user_id",nullable=false,length=26) val userId=userId
 @JdbcTypeCode(SqlTypes.CHAR) @Column(name="blog_id",nullable=false,length=26) val blogId=blogId
 @Column(name="friend_name",nullable=false,length=20) val friendName=friendName.trim()
 @Column(name="created_at",updatable=false) val createdAt=createdAt
 @Transient private var isNew=true
 override fun getId()=id
 override fun isNew()=isNew
 @PostPersist @PostLoad fun mark(){isNew=false}
}

@Entity
@Table(name="blog_fetch_states")
class BlogFetchState(blogId:String, now:Instant=Instant.now()):Persistable<String>{
 @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(name="blog_id",length=26) private val id:String=blogId
 @Column(nullable=false) val status="ACTIVE"
 @Column(name="last_attempt_at",nullable=false) val lastAttemptAt=now
 @Column(name="next_attempt_at",nullable=false) val nextAttemptAt=now
 @Column(name="consecutive_failure_count",nullable=false) val consecutiveFailureCount=0
 override fun getId()=id
 override fun isNew()=true
}
