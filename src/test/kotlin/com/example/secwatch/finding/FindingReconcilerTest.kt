package com.example.secwatch.finding

import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeOsvIntegrationTest
import com.example.secwatch.support.osvVulnerability
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("별칭 기반 중복 제거")
class FindingReconcilerTest : FakeOsvIntegrationTest() {
    @Autowired lateinit var findingReconciler: FindingReconciler
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository

    private var dependencyId = 0L

    @BeforeEach
    fun seed() {
        val project = projectRepository.save(Project("s", "s.example.com"))
        dependencyId = dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20")).id!!
    }

    private fun keys() = jdbcTemplate.queryForList("select vulnerability_key from finding_key", String::class.java)

    @Test
    @DisplayName("같은 취약점이 GHSA와 CVE 번호로 와도 항목은 하나다")
    fun same_vulnerability_under_ghsa_and_cve_is_one_finding() {
        val created = findingReconciler.reconcile(
            dependencyId,
            listOf(
                osvVulnerability("GHSA-1", listOf("CVE-2021-1")),
                osvVulnerability("CVE-2021-1", listOf("GHSA-1")),
            ),
        )

        assertThat(created).hasSize(1)
        assertThat(count("finding")).isEqualTo(1)
        assertThat(keys()).containsExactlyInAnyOrder("GHSA-1", "CVE-2021-1")
    }

    @Test
    @DisplayName("기존 항목에 새 별칭이 오면 새 항목을 만들지 않고 번호만 합친다")
    fun new_alias_on_existing_finding_merges_keys() {
        findingReconciler.reconcile(dependencyId, listOf(osvVulnerability("GHSA-1", listOf("CVE-2021-1"))))

        val created = findingReconciler.reconcile(dependencyId, listOf(osvVulnerability("PYSEC-9", listOf("CVE-2021-1"))))

        assertThat(created).isEmpty()
        assertThat(count("finding")).isEqualTo(1)
        assertThat(keys()).contains("PYSEC-9")
    }

    @Test
    @DisplayName("같은 결과로 다시 실행해도 새 항목이 생기지 않는다")
    fun second_run_creates_nothing() {
        val vulnerabilities = listOf(osvVulnerability("GHSA-1", listOf("CVE-2021-1")))
        findingReconciler.reconcile(dependencyId, vulnerabilities)

        assertThat(findingReconciler.reconcile(dependencyId, vulnerabilities)).isEmpty()
        assertThat(count("finding")).isEqualTo(1)
    }

    @Test
    @DisplayName("서로 다른 취약점은 서로 다른 항목이 된다")
    fun different_vulnerabilities_make_different_findings() {
        val created = findingReconciler.reconcile(
            dependencyId,
            listOf(osvVulnerability("GHSA-1"), osvVulnerability("GHSA-2")),
        )

        assertThat(created).hasSize(2)
        assertThat(count("finding")).isEqualTo(2)
    }

    @Test
    @DisplayName("처음 만든 항목은 새로 발견 상태다")
    fun status_defaults_to_new() {
        findingReconciler.reconcile(dependencyId, listOf(osvVulnerability("GHSA-1")))

        val row = jdbcTemplate.queryForMap("select status, primary_vulnerability_id from finding")
        assertThat(row["status"]).isEqualTo("NEW")
        assertThat(row["primary_vulnerability_id"]).isEqualTo("GHSA-1")
    }

    @Test
    @DisplayName("위험도 정보가 없으면 미분류로 저장한다")
    fun severity_unclassified_is_stored() {
        findingReconciler.reconcile(dependencyId, listOf(osvVulnerability("GHSA-1", severity = Severity.UNCLASSIFIED)))

        assertThat(jdbcTemplate.queryForObject("select severity from finding", String::class.java)).isEqualTo("UNCLASSIFIED")
    }

    @Test
    @DisplayName("의존성이 이미 지워졌으면 오류 없이 아무것도 만들지 않는다")
    fun missing_dependency_creates_nothing_and_does_not_fail() {
        val created = findingReconciler.reconcile(999999, listOf(osvVulnerability("GHSA-1")))

        assertThat(created).isEmpty()
        assertThat(count("finding")).isEqualTo(0)
    }
}
