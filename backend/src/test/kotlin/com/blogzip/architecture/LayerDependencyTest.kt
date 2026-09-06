package com.blogzip.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * 단일 모듈이므로 의존 방향을 컴파일러가 막지 못한다. 그 역할을 이 테스트가 대신한다.
 * docs/decisions/011-backend-module-structure.md
 *
 * 이 테스트를 지우거나 비활성화하면 011의 전제가 깨진다.
 *
 * `allowEmptyShould(true)`를 쓰는 이유: 아직 기능 패키지가 없어 규칙이 검사할 클래스가 0개다.
 * 기본 설정에서는 그 경우 실패한다. 규칙 자체는 유효하며 첫 기능 패키지가 생기는 순간부터 동작한다.
 */
class LayerDependencyTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
        .importPackages("com.blogzip")

    @Test
    fun `domain은 service, repository, controller를 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..service..", "..repository..", "..controller..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `domain은 Spring Web에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web..",
                "org.springframework.http..",
                "jakarta.servlet..",
            )
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `service는 controller를 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `응답 DTO는 엔티티에 의존하지 않는다`() {
        // 컨트롤러는 응답 DTO를 반환한다. 엔티티 노출은 feedUrl 등 내부 값 유출로 이어진다.
        // docs/decisions/007-persistence-stack.md, PRD P-002
        noClasses()
            .that().resideInAPackage("..controller..")
            .and().haveSimpleNameEndingWith("Response")
            .should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity::class.java)
            .allowEmptyShould(true)
            .check(classes)
    }
}
