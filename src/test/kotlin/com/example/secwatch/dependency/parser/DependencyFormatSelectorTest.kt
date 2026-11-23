package com.example.secwatch.dependency.parser

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.SecwatchException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("붙여넣은 내용의 형식 자동 선택")
class DependencyFormatSelectorTest {
    private val objectMapper = ObjectMapper()
    private val selector = DependencyFormatSelector(objectMapper, NpmLockParser(objectMapper), CycloneDxParser(objectMapper))

    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    @DisplayName("bomFormat이 CycloneDX이면 SBOM으로 읽는다")
    fun cyclonedx_is_read_as_sbom() {
        val result = selector.parse(fixture("cyclonedx-gradle-small.json"))

        assertThat(result.packages).contains(PackageReference("Maven", "org.springframework:spring-core", "6.1.5"))
    }

    @Test
    @DisplayName("package-lock.json은 전과 같은 결과로 읽는다")
    fun package_lock_reads_the_same_as_before() {
        val content = fixture("package-lock-v3-small.json")

        assertThat(selector.parse(content)).isEqualTo(NpmLockParser(objectMapper).parse(content))
    }

    @Test
    @DisplayName("어느 형식도 아닌 JSON은 지원하지 않는 형식으로 거부한다")
    fun unknown_json_throws_unsupported() {
        listOf("""{"hello":"world"}""", """[1,2]""", """{"bomFormat":"SPDX","packages":[]}""").forEach { content ->
            assertThat(assertThrows<SecwatchException> { selector.parse(content) }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
        }
    }

    @Test
    @DisplayName("JSON이 아니면 지원하지 않는 형식으로 거부한다")
    fun invalid_json_throws_unsupported() {
        assertThat(assertThrows<SecwatchException> { selector.parse("not json") }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
    }
}
