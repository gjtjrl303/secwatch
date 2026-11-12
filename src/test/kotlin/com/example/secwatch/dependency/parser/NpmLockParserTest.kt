package com.example.secwatch.dependency.parser

import com.example.secwatch.common.PackageRef
import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("package-lock.json 읽기")
class NpmLockParserTest {
    private val parser = NpmLockParser(ObjectMapper())

    private fun fixture() = javaClass.getResource("/fixtures/package-lock-v3-small.json")!!.readText()

    private fun npm(name: String, version: String) = PackageRef("npm", name, version)

    @Test
    @DisplayName("최상위와 중첩 패키지를 모두 읽는다")
    fun parses_top_level_and_nested() {
        val result = parser.parse(fixture())

        assertThat(result.packages).contains(
            npm("lodash", "4.17.20"),
            npm("a", "1.0.0"),
            npm("b", "2.0.0"),
        )
    }

    @Test
    @DisplayName("@scope/이름 형태의 패키지 이름을 읽는다")
    fun scoped_package_name() {
        assertThat(parser.parse(fixture()).packages).contains(npm("@scope/pkg", "1.2.3"))
    }

    @Test
    @DisplayName("이름이 같고 버전이 다른 패키지는 각각 남긴다")
    fun same_name_different_versions_kept() {
        val bs = parser.parse(fixture()).packages.filter { it.name == "b" }.map { it.version }
        assertThat(bs).containsExactlyInAnyOrder("1.0.0", "2.0.0")
    }

    @Test
    @DisplayName("링크와 버전 없는 항목은 건너뛰고 개수를 세며 루트는 세지 않는다")
    fun skips_link_and_versionless_but_not_root() {
        val result = parser.parse(fixture())

        assertThat(result.skipped).isEqualTo(2)
        assertThat(result.packages.map { it.name }).doesNotContain("demo", "linked", "no-version")
    }

    @Test
    @DisplayName("같은 이름과 버전이 여러 경로에 있으면 하나로 합친다")
    fun duplicates_collapsed() {
        val json = """{"lockfileVersion":3,"packages":{
            "":{},
            "node_modules/x":{"version":"1.0.0"},
            "node_modules/y/node_modules/x":{"version":"1.0.0"}}}"""

        assertThat(parser.parse(json).packages).containsExactly(npm("x", "1.0.0"))
    }

    @Test
    @DisplayName("lockfileVersion 1은 지원하지 않는 형식으로 거부한다")
    fun lockfile_v1_throws_unsupported() {
        val v1 = """{"lockfileVersion":1,"dependencies":{"a":{"version":"1.0.0"}}}"""
        assertThat(assertThrows<SecwatchException> { parser.parse(v1) }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
    }

    @Test
    @DisplayName("JSON이 아니면 지원하지 않는 형식으로 거부한다")
    fun invalid_json_throws_unsupported() {
        assertThat(assertThrows<SecwatchException> { parser.parse("not json") }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
    }

    @Test
    @DisplayName("읽을 라이브러리가 하나도 없으면 지원하지 않는 형식으로 거부한다")
    fun empty_packages_throws_unsupported() {
        val empty = """{"lockfileVersion":3,"packages":{"":{}}}"""
        assertThat(assertThrows<SecwatchException> { parser.parse(empty) }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
    }

    @Test
    @DisplayName("alias로 설치된 패키지는 경로의 별명이 아니라 실제 패키지 이름으로 저장한다")
    fun alias_uses_real_package_name() {
        val json = """{"lockfileVersion":3,"packages":{"":{},"node_modules/string-width-cjs":{"name":"string-width","version":"4.2.3"}}}"""

        assertThat(parser.parse(json).packages).containsExactly(npm("string-width", "4.2.3"))
    }

    @Test
    @DisplayName("name이 문자열이 아닌 항목이 있으면 지원하지 않는 형식으로 거부한다")
    fun non_text_name_throws_unsupported() {
        val json = """{"lockfileVersion":3,"packages":{"":{},"node_modules/lodash":{"name":123,"version":"4.17.21"}}}"""

        assertThat(assertThrows<SecwatchException> { parser.parse(json) }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
    }
}
