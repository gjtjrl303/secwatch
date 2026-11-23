package com.example.secwatch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("패키지 구조 규칙")
class ArchitectureTest {
    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages(BASE_PACKAGE)

    @Test
    @DisplayName("패키지끼리 서로 순환해서 가져다 쓰지 않는다")
    fun packages_are_free_of_cycles() {
        slices().matching("$BASE_PACKAGE.(*)..").should().beFreeOfCycles().check(classes)
    }

    @Test
    @DisplayName("패키지는 아래 패키지만 가져다 쓰고 위 패키지는 가져다 쓰지 않는다")
    fun packages_depend_only_on_packages_below() {
        var architecture = layeredArchitecture().consideringOnlyDependenciesInLayers()
        ALLOWED_DEPENDENCIES.keys.forEach { layer -> architecture = architecture.layer(layer).definedBy("$BASE_PACKAGE.$layer..") }
        ALLOWED_DEPENDENCIES.forEach { (layer, allowed) ->
            architecture = if (allowed.isEmpty()) {
                architecture.whereLayer(layer).mayNotAccessAnyLayer()
            } else {
                architecture.whereLayer(layer).mayOnlyAccessLayers(*allowed.toTypedArray())
            }
        }
        architecture.check(classes)
    }

    @Test
    @DisplayName("컨트롤러는 저장소를 직접 쓰지 않고 서비스를 거친다")
    fun controllers_do_not_use_repositories_directly() {
        noClasses().that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .check(classes)
    }

    @Test
    @DisplayName("허용 표에 없는 새 패키지가 생기면 실패한다")
    fun every_package_is_in_the_allowed_table() {
        val packages = classes.map { javaClass -> javaClass.packageName }
            .filter { packageName -> packageName.startsWith("$BASE_PACKAGE.") }
            .map { packageName -> packageName.removePrefix("$BASE_PACKAGE.").substringBefore('.') }
            .toSet()

        val missing = packages - ALLOWED_DEPENDENCIES.keys
        assertThat(missing)
            .describedAs("허용 표(ALLOWED_DEPENDENCIES)에 없는 패키지입니다. 가져다 써도 되는 아래 패키지를 정해서 표에 추가하세요.")
            .isEmpty()
    }

    private companion object {
        const val BASE_PACKAGE = "com.example.secwatch"

        /**
         * 패키지마다 가져다 써도 되는 아래 패키지다. common은 모두가 쓰는 맨 아래 패키지이고,
         * 위로 갈수록 application(점검과 알림을 잇는 곳), web(화면과 응답) 순이다.
         */
        val ALLOWED_DEPENDENCIES: Map<String, List<String>> = linkedMapOf(
            "common" to emptyList(),
            "osv" to listOf("common"),
            "project" to listOf("common"),
            "dependency" to listOf("common", "project"),
            "finding" to listOf("common", "project", "dependency", "osv"),
            // 알림 문구가 항목에 딸린 의존성의 이름과 버전을 쓴다(finding.dependency)
            "notify" to listOf("common", "project", "dependency", "finding"),
            "sitescan" to listOf("common", "project", "finding"),
            "application" to listOf("common", "project", "dependency", "finding", "notify", "sitescan"),
            "web" to listOf("common", "project", "dependency", "finding", "sitescan", "application"),
        )
    }
}
