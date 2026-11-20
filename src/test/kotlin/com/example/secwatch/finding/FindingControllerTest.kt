package com.example.secwatch.finding

import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.DbIntegrationTest
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@DisplayName("발견 항목 API")
class FindingControllerTest : DbIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository
    @Autowired lateinit var findingRepository: FindingRepository

    private lateinit var project: Project
    private lateinit var dependency: Dependency

    @BeforeEach
    fun seed() {
        project = projectRepository.save(Project("s", "s.example.com"))
        dependency = dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20"))
    }

    private fun saveFinding(vulnerabilityId: String, severity: Severity, status: FindingStatus = FindingStatus.NEW): Long {
        val finding = Finding(
            project = project,
            dependency = dependency,
            primaryVulnerabilityId = vulnerabilityId,
            summary = "summary of $vulnerabilityId",
            severity = severity,
            fixedVersions = "4.17.21",
            referenceUrl = "https://osv.dev/vulnerability/$vulnerabilityId",
            rawJson = "{}",
            firstSeenAt = LocalDateTime.now(),
        )
        if (status != FindingStatus.NEW) finding.changeStatus(status)
        return findingRepository.save(finding).id!!
    }

    private fun findings(query: String = "") = mockMvc.perform(get("/api/projects/${project.id}/findings$query"))

    private fun patchStatus(id: Long, status: String) = mockMvc.perform(
        patch("/api/findings/$id").contentType(MediaType.APPLICATION_JSON).content("""{"status":"$status"}""")
    )

    private fun historyRows() = jdbcTemplate.queryForList("select from_status, to_status from finding_status_history")

    @Test
    @DisplayName("상태와 위험도로 걸러서 조회한다")
    fun filters_by_status_and_severity() {
        saveFinding("A", Severity.HIGH)
        saveFinding("B", Severity.LOW)
        saveFinding("C", Severity.HIGH, FindingStatus.ACKNOWLEDGED)

        findings().andExpect(jsonPath("$.data.length()").value(3))
        findings("?severity=HIGH").andExpect(jsonPath("$.data.length()").value(2))
        findings("?status=NEW").andExpect(jsonPath("$.data.length()").value(2))
        findings("?status=NEW&severity=HIGH")
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].summary").value("summary of A"))
    }

    @Test
    @DisplayName("위험도가 높은 순으로 돌려주고 같으면 먼저 만든 항목이 앞이다")
    fun sorted_by_severity_then_id() {
        saveFinding("LOW-1", Severity.LOW)
        saveFinding("CRITICAL-1", Severity.CRITICAL)
        saveFinding("HIGH-1", Severity.HIGH)
        saveFinding("HIGH-2", Severity.HIGH)

        findings()
            .andExpect(jsonPath("$.data[0].severity").value("CRITICAL"))
            .andExpect(jsonPath("$.data[1].summary").value("summary of HIGH-1"))
            .andExpect(jsonPath("$.data[2].summary").value("summary of HIGH-2"))
            .andExpect(jsonPath("$.data[3].severity").value("LOW"))
    }

    @Test
    @DisplayName("패키지 이름, 버전, 고쳐진 버전, 원본 링크를 함께 돌려준다")
    fun list_includes_package_and_fix_details() {
        saveFinding("A", Severity.HIGH)

        findings()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].packageName").value("lodash"))
            .andExpect(jsonPath("$.data[0].version").value("4.17.20"))
            .andExpect(jsonPath("$.data[0].fixedVersions[0]").value("4.17.21"))
            .andExpect(jsonPath("$.data[0].referenceUrl").value("https://osv.dev/vulnerability/A"))
            .andExpect(jsonPath("$.data[0].status").value("NEW"))
    }

    @Test
    @DisplayName("잘못된 상태나 위험도 값은 400과 안내 문구를 돌려준다")
    fun invalid_filter_values_are_400() {
        findings("?status=NOPE")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40005))
            .andExpect(jsonPath("$.message").value("올바르지 않은 상태입니다."))
        findings("?severity=NOPE")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40006))
            .andExpect(jsonPath("$.message").value("올바르지 않은 위험도입니다."))
    }

    @Test
    @DisplayName("없는 프로젝트의 항목을 조회하면 404를 돌려준다")
    fun list_for_unknown_project_is_404() {
        mockMvc.perform(get("/api/projects/999999/findings"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("상태를 바꾸면 저장하고 변경 이력을 남긴다")
    fun patch_changes_status_and_writes_history() {
        val id = saveFinding("A", Severity.HIGH)

        patchStatus(id, "ACKNOWLEDGED")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
            .andExpect(jsonPath("$.data.packageName").value("lodash"))

        assertThat(jdbcTemplate.queryForObject("select status from finding where id = ?", String::class.java, id))
            .isEqualTo("ACKNOWLEDGED")
        assertThat(historyRows()).hasSize(1)
        assertThat(historyRows()[0]["from_status"]).isEqualTo("NEW")
        assertThat(historyRows()[0]["to_status"]).isEqualTo("ACKNOWLEDGED")
    }

    @Test
    @DisplayName("같은 상태로 바꾸면 이력을 남기지 않는다")
    fun patch_same_status_writes_no_history() {
        val id = saveFinding("A", Severity.HIGH)

        patchStatus(id, "NEW").andExpect(status().isOk)

        assertThat(historyRows()).isEmpty()
    }

    @Test
    @DisplayName("여러 번 바꾸면 바꾼 순서대로 이력이 쌓인다")
    fun history_accumulates_in_order() {
        val id = saveFinding("A", Severity.HIGH)

        patchStatus(id, "ACKNOWLEDGED").andExpect(status().isOk)
        patchStatus(id, "RESOLVED").andExpect(status().isOk)

        val rows = jdbcTemplate.queryForList("select from_status, to_status from finding_status_history order by id")
        assertThat(rows.map { row -> row["from_status"] to row["to_status"] })
            .containsExactly("NEW" to "ACKNOWLEDGED", "ACKNOWLEDGED" to "RESOLVED")
    }

    @Test
    @DisplayName("잘못된 상태는 400, 없는 항목은 404를 돌려준다")
    fun patch_invalid_status_is_400_and_unknown_finding_is_404() {
        val id = saveFinding("A", Severity.HIGH)

        patchStatus(id, "NOPE")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("올바르지 않은 상태입니다."))
        patchStatus(999999, "RESOLVED")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(40402))
            .andExpect(jsonPath("$.message").value("발견 항목을 찾을 수 없습니다."))
        assertThat(historyRows()).isEmpty()
    }

    @Test
    @DisplayName("프로젝트를 지우면 딸린 항목과 변경 이력도 함께 지워진다")
    fun deleting_project_cascades_findings_and_history() {
        val id = saveFinding("A", Severity.HIGH)
        patchStatus(id, "RESOLVED").andExpect(status().isOk)

        jdbcTemplate.update("delete from project where id = ?", project.id)

        assertThat(count("finding")).isEqualTo(0)
        assertThat(count("finding_status_history")).isEqualTo(0)
    }
}
