package com.blogzip.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 통합 테스트 베이스.
 *
 * 운영과 같은 PostgreSQL에 Flyway 마이그레이션을 적용해 스키마를 만든다.
 * Hibernate 자동 생성 스키마로 테스트하지 않는다.
 * docs/decisions/001-database-selection.md, docs/decisions/008-schema-migration.md
 *
 * Docker가 필요하다.
 */
@ActiveProfiles("test")
@SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers = [PostgresInitializer::class])
abstract class IntegrationTest
