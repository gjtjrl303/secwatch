package com.example.secwatch.verify

import com.example.secwatch.dependency.DependencyImporter
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.MySqlIntegrationTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 손으로 돌리는 검증용 테스트다. VERIFY_LOCKFILE을 주지 않으면 건너뛴다.
 * 실제 package-lock.json을 실제 OSV로 점검하고 Grype JSON 결과와 비교해 build/verification/comparison.txt에 쓴다.
 *   VERIFY_LOCKFILE=<package-lock.json> VERIFY_GRYPE_JSON=<grype -o json 결과> ./gradlew test --tests '*JuiceShopComparisonTest' -i
 */
@EnabledIfEnvironmentVariable(named = "VERIFY_LOCKFILE", matches = ".+")
@DisplayName("Juice Shop 비교 (직접 실행용)")
class JuiceShopComparisonTest : MySqlIntegrationTest() {
    @Autowired lateinit var dependencyImporter: DependencyImporter
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @DisplayName("Juice Shop의 package-lock.json을 점검해 Grype 결과와 비교한다")
    fun compare_with_grype() {
        val lockfile = Files.readString(Path.of(System.getenv("VERIFY_LOCKFILE")))
        val grypeJson = Files.readString(Path.of(System.getenv("VERIFY_GRYPE_JSON")))
        val projectId = projectRepository.save(Project("juice-shop", "juice-shop.example.com")).id!!

        val imported = dependencyImporter.import(projectId, lockfile)

        // 가져온 직후 자동 점검(#33)이 끝나기를 기다린다. 성공하면 점검 시각이, 실패하면 오류가 기록된다.
        Awaitility.await().atMost(Duration.ofMinutes(20)).pollInterval(Duration.ofSeconds(2)).until {
            jdbcTemplate.queryForObject(
                "select last_checked_at is not null or last_check_error is not null from project where id = ?",
                Boolean::class.java, projectId,
            ) == true
        }
        assertThat(jdbcTemplate.queryForObject("select last_check_error from project where id = ?", String::class.java, projectId))
            .isNull()

        val ourFindings = ourFindings(projectId)
        val ours = ourFindings.map { finding -> finding.keys }
        val grype = GrypeComparer.fromGrypeJson(grypeJson)
        val result = GrypeComparer.compare(ours, grype)
        // 집계와 같은 묶음 분류를 써서, 별칭을 거쳐 이어진 항목이 단독 목록에 섞이지 않게 한다
        val only = GrypeComparer.onlyItems(ours, grype)
        val onlyOurs = only.ours.map { index -> ourFindings[index] }
        val onlyGrype = only.grype.map { index -> grype[index] }

        val report = buildString {
            appendLine("packages parsed: ${imported.parsed} (skipped ${imported.skipped})")
            appendLine("our findings: ${ours.size}, grype matches (as listed): ${grype.size}")
            appendLine("both=${result.both} onlyOurs=${result.onlyOurs} onlyGrype=${result.onlyGrype}")
            appendLine("-- only ours --")
            onlyOurs.forEach { finding -> appendLine(finding.label) }
            appendLine("-- only grype --")
            onlyGrype.forEach { keys -> appendLine(keys.sorted().joinToString(" / ")) }
        }
        Files.createDirectories(Path.of("build/verification"))
        Files.writeString(Path.of("build/verification/comparison.txt"), report)
        println(report)
        assertThat(ours).isNotEmpty()
    }

    private data class OurFinding(val keys: Set<String>, val label: String)

    private fun ourFindings(projectId: Long): List<OurFinding> {
        val keysByFinding = LinkedHashMap<Long, MutableSet<String>>()
        val labels = HashMap<Long, String>()
        jdbcTemplate.query(
            """
            select f.id, d.name, d.version, k.vulnerability_key, f.severity, f.primary_vulnerability_id, f.summary
            from finding f
              join dependency d on d.id = f.dependency_id
              join finding_key k on k.finding_id = f.id
            where f.project_id = ?
            """.trimIndent(),
            { rs ->
                val id = rs.getLong(1)
                keysByFinding.getOrPut(id) { mutableSetOf() } += GrypeComparer.key(rs.getString(2), rs.getString(3), rs.getString(4))
                labels[id] = "${rs.getString(2)}@${rs.getString(3)} [${rs.getString(5)}] ${rs.getString(6)} ${rs.getString(7)}"
            },
            projectId,
        )
        return keysByFinding.map { (id, keys) -> OurFinding(keys.toSet(), labels.getValue(id)) }
    }
}
