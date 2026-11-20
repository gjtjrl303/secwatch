package com.example.secwatch.finding

import com.example.secwatch.common.PackageReference
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import com.example.secwatch.support.osvVulnerability
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("취약점 점검 서비스")
class CheckServiceTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var checkService: CheckService
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository

    private var projectId = 0L
    private lateinit var lodash: Dependency
    private val lodashReference = PackageReference("npm", "lodash", "4.17.20")
    private val errorMessage = "취약점 정보를 불러오지 못했습니다. 다음 주기에 다시 시도합니다."

    @BeforeEach
    fun seed() {
        val project = projectRepository.save(Project("s", "s.example.com"))
        projectId = project.id!!
        lodash = dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20"))
        fakeVulnerabilitySource.idsByPackageReference = mapOf(lodashReference to listOf("GHSA-1"))
        fakeVulnerabilitySource.vulnerabilities = mapOf("GHSA-1" to osvVulnerability("GHSA-1", listOf("CVE-2021-1")))
    }

    private fun lastCheckError() =
        jdbcTemplate.queryForObject("select last_check_error from project where id = ?", String::class.java, projectId)

    private fun lastCheckedAt() =
        jdbcTemplate.queryForObject("select last_checked_at from project where id = ?", Any::class.java, projectId)

    @Test
    @DisplayName("점검하면 항목을 만들고 마지막 점검 시각을 기록하며 오류를 비운다")
    fun check_creates_finding_and_marks_project_checked() {
        val result = checkService.check(projectId)

        assertThat(result.newFindingIds).hasSize(1)
        assertThat(count("finding")).isEqualTo(1)
        assertThat(lastCheckedAt()).isNotNull
        assertThat(lastCheckError()).isNull()
    }

    @Test
    @DisplayName("두 번째 점검은 새 항목이 없다고 알린다")
    fun second_check_reports_no_new_findings() {
        checkService.check(projectId)

        assertThat(checkService.check(projectId).newFindingIds).isEmpty()
        assertThat(count("finding")).isEqualTo(1)
    }

    @Test
    @DisplayName("철회된 취약점은 무시한다")
    fun withdrawn_vulnerability_is_ignored() {
        fakeVulnerabilitySource.vulnerabilities = mapOf("GHSA-1" to null)

        assertThat(checkService.check(projectId).newFindingIds).isEmpty()
        assertThat(count("finding")).isEqualTo(0)
        assertThat(lastCheckedAt()).isNotNull
    }

    @Test
    @DisplayName("의존성을 지우면 딸린 항목도 함께 지워진다")
    fun removed_dependency_cascades_findings() {
        checkService.check(projectId)
        assertThat(count("finding")).isEqualTo(1)
        assertThat(count("finding_key")).isEqualTo(2)

        dependencyRepository.deleteById(lodash.id!!)

        assertThat(count("finding")).isEqualTo(0)
        assertThat(count("finding_key")).isEqualTo(0)
    }

    @Test
    @DisplayName("OSV 장애가 나도 기존 항목은 남기고 오류를 기록한다")
    fun osv_failure_keeps_existing_findings_and_records_error() {
        checkService.check(projectId)
        fakeVulnerabilitySource.failIdLookup = true

        val result = checkService.check(projectId)

        assertThat(result.newFindingIds).isEmpty()
        assertThat(count("finding")).isEqualTo(1)
        assertThat(lastCheckError()).isEqualTo(errorMessage)
    }

    @Test
    @DisplayName("점검하는 동안 의존성이 바뀌면 점검 완료로 표시하지 않고 기존 오류도 지우지 않는다")
    fun dependencies_changed_during_the_check_do_not_mark_it_checked() {
        jdbcTemplate.update("update project set last_check_error = ? where id = ?", errorMessage, projectId)
        fakeVulnerabilitySource.onFindVulnerabilityIds = {
            val project = projectRepository.findById(projectId).get()
            dependencyRepository.save(Dependency(project, "npm", "express", "4.0.0"))
        }

        checkService.check(projectId)

        assertThat(lastCheckedAt()).isNull()
        assertThat(lastCheckError()).isEqualTo(errorMessage)
    }

    @Test
    @DisplayName("상세 조회가 하나라도 실패하면 아무것도 저장하지 않는다")
    fun partial_detail_failure_persists_nothing() {
        fakeVulnerabilitySource.idsByPackageReference = mapOf(lodashReference to listOf("GHSA-1", "GHSA-2"))
        fakeVulnerabilitySource.vulnerabilities = mapOf(
            "GHSA-1" to osvVulnerability("GHSA-1"),
            "GHSA-2" to osvVulnerability("GHSA-2"),
        )
        fakeVulnerabilitySource.failDetailFor = "GHSA-2"

        val result = checkService.check(projectId)

        assertThat(result.newFindingIds).isEmpty()
        assertThat(count("finding")).isEqualTo(0)
        assertThat(lastCheckError()).isEqualTo(errorMessage)
    }

    @Test
    @DisplayName("점검에 성공하면 이전 오류 표시를 지운다")
    fun successful_check_clears_previous_error() {
        fakeVulnerabilitySource.failIdLookup = true
        checkService.check(projectId)
        assertThat(lastCheckError()).isEqualTo(errorMessage)

        fakeVulnerabilitySource.failIdLookup = false
        checkService.check(projectId)

        assertThat(lastCheckError()).isNull()
    }

    @Test
    @DisplayName("다른 점검이 커밋되는 중에 시작해도 같은 취약점이 중복 항목이 되지 않는다")
    fun check_started_during_another_commit_does_not_duplicate_the_finding() {
        // 다른 점검이 커밋 중이다. 프로젝트 잠금을 쥐고 이 의존성에 GHSA-1을 이미 넣었다
        val result = blockedWhileProjectIsLocked(
            projectId,
            uncommitted = {
                jdbcTemplate.update(
                    "insert into finding (project_id, dependency_id, primary_vulnerability_id, summary, severity, reference_url, raw_json, first_seen_at) " +
                        "values (?, ?, 'GHSA-1', 's', 'HIGH', 'https://osv.dev/vulnerability/GHSA-1', '{}', now(6))",
                    projectId, lodash.id,
                )
                val findingId = jdbcTemplate.queryForObject("select last_insert_id()", Long::class.java)!!
                jdbcTemplate.update(
                    "insert into finding_key (finding_id, dependency_id, vulnerability_key) values (?, ?, 'GHSA-1')",
                    findingId, lodash.id,
                )
            },
        ) { checkService.check(projectId) }

        assertThat(result.newFindingIds).isEmpty()
        assertThat(count("finding")).isEqualTo(1)
    }

    @Test
    @DisplayName("예상 밖의 실패도 조용히 넘기지 않고 오류로 기록한다")
    fun unexpected_failure_is_recorded_not_silent() {
        fakeVulnerabilitySource.explodeForPackageName = "lodash"

        val result = checkService.check(projectId)

        assertThat(result.newFindingIds).isEmpty()
        assertThat(lastCheckError()).isEqualTo(errorMessage)
    }

    @Test
    @DisplayName("고쳐진 버전 목록이 아주 길어도 점검이 실패하지 않는다")
    fun very_long_fixed_versions_do_not_fail_the_check() {
        fakeVulnerabilitySource.vulnerabilities = mapOf(
            "GHSA-1" to osvVulnerability("GHSA-1", fixedVersions = (1..300).map { number -> "1.0.$number-long-suffix" }),
        )

        val result = checkService.check(projectId)

        assertThat(result.newFindingIds).hasSize(1)
        assertThat(lastCheckError()).isNull()
        assertThat(jdbcTemplate.queryForObject("select char_length(fixed_versions) from finding", Int::class.java))
            .isLessThanOrEqualTo(1000)
    }
}
