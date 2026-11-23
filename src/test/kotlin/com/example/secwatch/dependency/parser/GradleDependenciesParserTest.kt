package com.example.secwatch.dependency.parser

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.SecwatchException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Gradle dependencies 출력 읽기")
class GradleDependenciesParserTest {
    private val parser = GradleDependenciesParser()

    private fun fixture() = javaClass.getResource("/fixtures/gradle-dependencies-small.txt")!!.readText()

    private fun maven(name: String, version: String) = PackageReference("Maven", name, version)

    private fun tree(vararg lines: String) = lines.joinToString("\n")

    @Test
    @DisplayName("전체 출력에서는 실행할 때 쓰는 구성(runtimeClasspath)의 라이브러리만 읽는다")
    fun full_output_reads_only_runtime_classpath() {
        val result = parser.parse(fixture())

        assertThat(result.packages).containsExactlyInAnyOrder(
            maven("org.springframework.boot:spring-boot-starter-web", "3.4.2"),
            maven("org.springframework:spring-web", "6.2.2"),
            maven("org.springframework:spring-core", "6.2.2"),
            maven("com.fasterxml.jackson.core:jackson-databind", "2.18.2"),
            maven("com.fasterxml.jackson:jackson-bom", "2.18.2"),
            maven("io.jsonwebtoken:jjwt-jackson", "0.11.5"),
            maven("org.jsoup:jsoup", "1.15.3"),
            maven("com.mysql:mysql-connector-j", "9.1.0"),
        )
        assertThat(result.skipped).isEqualTo(0)
    }

    @Test
    @DisplayName("구성 이름 없이 트리 줄만 붙여넣어도 읽는다")
    fun tree_lines_without_header_are_read() {
        val result = parser.parse(tree("+--- a:b:1.0", "\\--- c:d:2.0"))

        assertThat(result.packages).containsExactly(maven("a:b", "1.0"), maven("c:d", "2.0"))
    }

    @Test
    @DisplayName("runtimeClasspath가 없으면 있는 구성을 모두 읽는다")
    fun without_runtime_classpath_all_configurations_are_read() {
        val result = parser.parse(tree("compileClasspath - Compile classpath.", "\\--- a:b:1.0", "", "testCompileClasspath", "\\--- c:d:2.0"))

        assertThat(result.packages).containsExactly(maven("a:b", "1.0"), maven("c:d", "2.0"))
    }

    @Test
    @DisplayName("여러 모듈의 출력이 이어져 있으면 모듈마다의 runtimeClasspath를 모두 읽는다")
    fun runtime_classpath_of_every_module_is_read() {
        val result = parser.parse(
            tree(
                "Project ':api'", "runtimeClasspath - Runtime classpath of source set 'main'.", "\\--- a:b:1.0", "",
                "testRuntimeClasspath - Runtime classpath of source set 'test'.", "\\--- t:t:9.9", "",
                "Project ':core'", "runtimeClasspath - Runtime classpath of source set 'main'.", "\\--- c:d:2.0",
            )
        )

        assertThat(result.packages).containsExactly(maven("a:b", "1.0"), maven("c:d", "2.0"))
    }

    @Test
    @DisplayName("화살표(->)가 있으면 그 뒤의 최종 버전을 쓴다")
    fun resolved_version_after_arrow_wins() {
        val result = parser.parse(tree("+--- a:b:1.0 -> 2.0", "+--- c:d -> 3.0", "\\--- e:f:{strictly 1.0} -> 1.0 (*)"))

        assertThat(result.packages).containsExactly(maven("a:b", "2.0"), maven("c:d", "3.0"), maven("e:f", "1.0"))
    }

    @Test
    @DisplayName("화살표 뒤가 다른 라이브러리이면 바뀐 라이브러리로 읽는다")
    fun substituted_module_after_arrow() {
        assertThat(parser.parse("\\--- a:b:1.0 -> c:d:2.0").packages).containsExactly(maven("c:d", "2.0"))
    }

    @Test
    @DisplayName("반복 표시(*)가 붙은 줄은 한 번만 남긴다")
    fun repeated_marker_is_collapsed() {
        val result = parser.parse(tree("+--- a:b:1.0", "|    \\--- c:d:2.0", "\\--- c:d:2.0 (*)"))

        assertThat(result.packages).containsExactly(maven("a:b", "1.0"), maven("c:d", "2.0"))
        assertThat(result.skipped).isEqualTo(0)
    }

    @Test
    @DisplayName("제약 표시(c)와 풀리지 않은 표시(n)와 하위 프로젝트 줄은 저장하지 않고 건너뛴 개수에도 세지 않는다")
    fun constraint_unresolvable_and_project_lines_are_ignored() {
        val result = parser.parse(tree("+--- a:b:1.0", "+--- x:y:9.9 (c)", "+--- x:z (n)", "\\--- project :core"))

        assertThat(result.packages).containsExactly(maven("a:b", "1.0"))
        assertThat(result.skipped).isEqualTo(0)
    }

    @Test
    @DisplayName("내려받지 못한 줄(FAILED)과 읽을 수 없는 줄은 건너뛰고 개수를 센다")
    fun failed_and_unreadable_lines_are_skipped() {
        val result = parser.parse(tree("+--- a:b:1.0", "+--- x:y:9.9 FAILED", "+--- no-colon", "\\--- g:n"))

        assertThat(result.packages).containsExactly(maven("a:b", "1.0"))
        assertThat(result.skipped).isEqualTo(3)
    }

    @Test
    @DisplayName("저장할 수 있는 길이를 넘는 이름이나 버전은 건너뛴다")
    fun too_long_name_or_version_is_skipped() {
        val result = parser.parse(tree("+--- g:${"a".repeat(255)}:1.0", "+--- c:d:${"1".repeat(101)}", "\\--- a:b:1.0"))

        assertThat(result.packages).containsExactly(maven("a:b", "1.0"))
        assertThat(result.skipped).isEqualTo(2)
    }

    @Test
    @DisplayName("윈도우 줄바꿈(CRLF)이어도 읽는다")
    fun crlf_line_endings() {
        assertThat(parser.parse("+--- a:b:1.0\r\n\\--- c:d:2.0 (*)\r\n").packages).containsExactly(maven("a:b", "1.0"), maven("c:d", "2.0"))
    }

    @Test
    @DisplayName("트리 줄이 없거나 읽을 라이브러리가 하나도 없으면 지원하지 않는 형식으로 거부한다")
    fun nothing_readable_throws_unsupported() {
        listOf("not gradle output", "runtimeClasspath\nNo dependencies", "implementation (n)\n\\--- a:b (n)").forEach { content ->
            assertThat(assertThrows<SecwatchException> { parser.parse(content) }.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT)
        }
    }
}
