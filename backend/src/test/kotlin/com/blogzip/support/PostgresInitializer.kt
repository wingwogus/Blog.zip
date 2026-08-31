package com.blogzip.support

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.PostgreSQLContainer

/**
 * 테스트 전체가 하나의 PostgreSQL 컨테이너를 공유한다.
 * 클래스마다 컨테이너를 띄우면 테스트 시간이 컨테이너 기동 시간에 지배된다.
 *
 * docs/decisions/001-database-selection.md 4장
 */
class PostgresInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            "spring.datasource.url=${CONTAINER.jdbcUrl}",
            "spring.datasource.username=${CONTAINER.username}",
            "spring.datasource.password=${CONTAINER.password}",
        ).applyTo(applicationContext.environment)
    }

    companion object {
        // 운영과 같은 major 버전을 쓴다.
        private val CONTAINER: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName("blogzip")
                .withUsername("blogzip")
                .withPassword("blogzip")
                .also { it.start() }
    }
}
