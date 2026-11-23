package com.example.secwatch.dependency.parser

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.SecwatchException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("CycloneDX SBOM 읽기")
class CycloneDxParserTest {
    private val cycloneDxParser = CycloneDxParser(ObjectMapper())

    private fun fixture() = javaClass.getResource("/fixtures/cyclonedx-gradle-small.json")!!.readText()

    private fun sbom(vararg purls: String): String {
        val components = purls.joinToString(",") { purl -> """{"type":"library","purl":"$purl"}""" }
        return """{"bomFormat":"CycloneDX","specVersion":"1.5","components":[$components]}"""
    }

    private fun parseOne(purl: String) = cycloneDxParser.parse(sbom(purl)).packages.single()

    @Test
    @DisplayName("Gradle SBOM의 라이브러리를 Maven 에코시스템과 그룹:아티팩트 이름으로 읽는다")
    fun gradle_sbom_reads_maven_group_and_artifact() {
        val result = cycloneDxParser.parse(fixture())

        assertThat(result.packages).contains(
            PackageReference("Maven", "org.springframework:spring-core", "6.1.5"),
            PackageReference("Maven", "com.fasterxml.jackson.core:jackson-databind", "2.15.0"),
        )
    }

    @Test
    @DisplayName("구성 요소 안에 중첩된 구성 요소도 읽는다")
    fun nested_components_are_read() {
        assertThat(cycloneDxParser.parse(fixture()).packages)
            .contains(PackageReference("Maven", "org.yaml:snakeyaml", "1.33"))
    }

    @Test
    @DisplayName("SBOM을 만든 프로젝트 자신(metadata.component)은 읽지 않는다")
    fun root_component_is_not_read() {
        assertThat(cycloneDxParser.parse(fixture()).packages.map { it.name }).doesNotContain("com.example:demo")
    }

    @Test
    @DisplayName("purl이 없거나 지원하지 않는 종류인 항목은 건너뛰고 개수를 센다")
    fun skips_missing_purl_and_unknown_type() {
        val result = cycloneDxParser.parse(fixture())

        assertThat(result.packages).hasSize(3)
        assertThat(result.skipped).isEqualTo(2)
    }

    @Test
    @DisplayName("버전이 없는 purl은 건너뛴다")
    fun versionless_purl_is_skipped() {
        val result = cycloneDxParser.parse(sbom("pkg:maven/a/b", "pkg:maven/c/d@1.0"))

        assertThat(result.packages).containsExactly(PackageReference("Maven", "c:d", "1.0"))
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    @DisplayName("그룹이 없는 maven purl은 건너뛴다")
    fun maven_without_group_is_skipped() {
        val result = cycloneDxParser.parse(sbom("pkg:maven/alone@1.0", "pkg:maven/c/d@1.0"))

        assertThat(result.packages).containsExactly(PackageReference("Maven", "c:d", "1.0"))
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    @DisplayName("purl 뒤의 한정자(?)와 하위 경로(#)는 버전에 넣지 않는다")
    fun qualifiers_and_subpath_are_dropped() {
        assertThat(parseOne("pkg:maven/c/d@1.0?type=jar&classifier=x#sub/path"))
            .isEqualTo(PackageReference("Maven", "c:d", "1.0"))
    }

    @Test
    @DisplayName("퍼센트로 인코딩된 이름과 버전을 풀어서 읽는다")
    fun percent_encoding_is_decoded() {
        assertThat(parseOne("pkg:npm/%40angular/core@17.0.0")).isEqualTo(PackageReference("npm", "@angular/core", "17.0.0"))
        assertThat(parseOne("pkg:maven/c/d@1.0%2Bbuild")).isEqualTo(PackageReference("Maven", "c:d", "1.0+build"))
    }

    @Test
    @DisplayName("npm은 스코프가 인코딩되지 않아도 @스코프/이름으로 읽는다")
    fun npm_unencoded_scope() {
        assertThat(parseOne("pkg:npm/@angular/core@17.0.0")).isEqualTo(PackageReference("npm", "@angular/core", "17.0.0"))
        assertThat(parseOne("pkg:npm/lodash@4.17.20")).isEqualTo(PackageReference("npm", "lodash", "4.17.20"))
    }

    @Test
    @DisplayName("pypi 이름은 소문자와 하이픈으로 정규화한다")
    fun pypi_name_is_normalized() {
        assertThat(parseOne("pkg:pypi/Flask_SQLAlchemy@3.0.0")).isEqualTo(PackageReference("PyPI", "flask-sqlalchemy", "3.0.0"))
    }

    @Test
    @DisplayName("cargo는 crates.io 에코시스템으로 읽는다")
    fun cargo_maps_to_crates_io() {
        assertThat(parseOne("pkg:cargo/serde@1.0.200")).isEqualTo(PackageReference("crates.io", "serde", "1.0.200"))
    }

    @Test
    @DisplayName("종류의 대소문자가 달라도 읽는다")
    fun type_is_case_insensitive() {
        assertThat(parseOne("pkg:Maven/c/d@1.0")).isEqualTo(PackageReference("Maven", "c:d", "1.0"))
    }

    @Test
    @DisplayName("같은 라이브러리가 여러 번 나오면 하나로 합친다")
    fun duplicates_collapsed() {
        val result = cycloneDxParser.parse(sbom("pkg:maven/c/d@1.0?type=jar", "pkg:maven/c/d@1.0?type=pom"))

        assertThat(result.packages).containsExactly(PackageReference("Maven", "c:d", "1.0"))
    }

    @Test
    @DisplayName("저장할 수 있는 길이를 넘는 이름이나 버전은 건너뛴다")
    fun too_long_name_or_version_is_skipped() {
        val result = cycloneDxParser.parse(
            sbom("pkg:maven/g/${"a".repeat(255)}@1.0", "pkg:maven/c/d@${"1".repeat(101)}", "pkg:maven/c/d@1.0")
        )

        assertThat(result.packages).containsExactly(PackageReference("Maven", "c:d", "1.0"))
        assertThat(result.skipped).isEqualTo(2)
    }

    @Test
    @DisplayName("읽을 라이브러리가 하나도 없으면 지원하지 않는 형식으로 거부한다")
    fun no_readable_component_throws_unsupported() {
        val empty = """{"bomFormat":"CycloneDX","components":[]}"""
        val missing = """{"bomFormat":"CycloneDX"}"""

        assertThat(assertThrows<SecwatchException> { cycloneDxParser.parse(empty) }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
        assertThat(assertThrows<SecwatchException> { cycloneDxParser.parse(missing) }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
    }

    @Test
    @DisplayName("JSON이 아니면 지원하지 않는 형식으로 거부한다")
    fun invalid_json_throws_unsupported() {
        assertThat(assertThrows<SecwatchException> { cycloneDxParser.parse("<bom/>") }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
    }
}
