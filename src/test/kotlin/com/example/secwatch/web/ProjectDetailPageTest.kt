package com.example.secwatch.web

import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.finding.Finding
import com.example.secwatch.finding.FindingRepository
import com.example.secwatch.finding.FindingStatus
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@AutoConfigureMockMvc
@DisplayName("프로젝트 상세 화면의 조작")
class ProjectDetailPageTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository
    @Autowired lateinit var findingRepository: FindingRepository

    private val webhook = "https://discord.com/api/webhooks/1/topsecret"
    private val lockfile = """{"lockfileVersion":3,"packages":{"":{},"node_modules/express":{"version":"4.17.1"}}}"""
    private lateinit var project: Project

    @BeforeEach
    fun seed() {
        project = projectRepository.save(Project("모꼬지", "mokkoji.example.com"))
    }

    private fun saveFinding(
        vulnerabilityId: String,
        severity: Severity = Severity.HIGH,
        status: FindingStatus = FindingStatus.NEW,
        packageName: String = "lodash",
        owner: Project = project,
    ): Finding {
        val dependency = dependencyRepository.findByProjectId(owner.id!!).firstOrNull { saved -> saved.name == packageName }
            ?: dependencyRepository.save(Dependency(owner, "npm", packageName, "4.17.20"))
        val finding = Finding(
            project = owner,
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
        return findingRepository.save(finding)
    }

    private fun statusOf(findingId: Long) =
        jdbcTemplate.queryForObject("select status from finding where id = ?", String::class.java, findingId)

    private fun page(path: String = "/projects/${project.id}") = mockMvc.perform(get(path))

    @Test
    @DisplayName("항목을 패키지별로 묶고 가장 위험한 묶음을 위에 보여준다")
    fun findings_are_grouped_by_package_with_the_riskiest_first() {
        saveFinding("GHSA-L1", Severity.LOW, packageName = "aaa")
        saveFinding("GHSA-H1", Severity.HIGH, packageName = "lodash")
        saveFinding("GHSA-H2", Severity.HIGH, packageName = "lodash")

        val html = page().andExpect(status().isOk).andReturn().response.contentAsString

        assertThat(html).contains("lodash@4.17.20").contains("aaa@4.17.20").contains("2건")
        assertThat(html.indexOf("lodash@4.17.20")).isLessThan(html.indexOf("aaa@4.17.20"))
    }

    @Test
    @DisplayName("위 요약에 열린 취약점 개수를 위험도별로 보여준다")
    fun summary_counts_open_findings() {
        saveFinding("A", Severity.HIGH)
        saveFinding("B", Severity.CRITICAL, FindingStatus.ACKNOWLEDGED)
        saveFinding("C", Severity.HIGH, FindingStatus.RESOLVED)

        page()
            .andExpect(content().string(containsString("열린 취약점 2건")))
            .andExpect(content().string(containsString("치명 1")))
            .andExpect(content().string(containsString("높음 1")))
    }

    @Test
    @DisplayName("위험도로 거르면 그 위험도만 보인다")
    fun filter_by_severity_shows_only_that_severity() {
        saveFinding("GHSA-HIGH-ONE", Severity.HIGH, packageName = "a")
        saveFinding("GHSA-LOW-ONE", Severity.LOW, packageName = "b")

        page("/projects/${project.id}?severity=HIGH")
            .andExpect(content().string(containsString("GHSA-HIGH-ONE")))
            .andExpect(content().string(not(containsString("GHSA-LOW-ONE"))))
    }

    @Test
    @DisplayName("열린 것으로 거르면 해결함과 해당 없음을 숨기고 전체는 모두 보여준다")
    fun open_filter_hides_closed_findings_and_all_shows_them() {
        saveFinding("GHSA-NEW-ONE", status = FindingStatus.NEW, packageName = "a")
        saveFinding("GHSA-DONE-ONE", status = FindingStatus.RESOLVED, packageName = "b")
        saveFinding("GHSA-NA-ONE", status = FindingStatus.NOT_APPLICABLE, packageName = "c")

        page("/projects/${project.id}?status=open")
            .andExpect(content().string(containsString("GHSA-NEW-ONE")))
            .andExpect(content().string(not(containsString("GHSA-DONE-ONE"))))
            .andExpect(content().string(not(containsString("GHSA-NA-ONE"))))
        page("/projects/${project.id}?status=all")
            .andExpect(content().string(containsString("GHSA-DONE-ONE")))
            .andExpect(content().string(containsString("GHSA-NA-ONE")))
    }

    @Test
    @DisplayName("특정 상태로도 거를 수 있다")
    fun filter_by_a_single_status() {
        saveFinding("GHSA-NEW-ONE", status = FindingStatus.NEW, packageName = "a")
        saveFinding("GHSA-DONE-ONE", status = FindingStatus.RESOLVED, packageName = "b")

        page("/projects/${project.id}?status=RESOLVED")
            .andExpect(content().string(containsString("GHSA-DONE-ONE")))
            .andExpect(content().string(not(containsString("GHSA-NEW-ONE"))))
    }

    @Test
    @DisplayName("잘못된 필터 값은 오류 없이 무시한다")
    fun invalid_filter_values_are_ignored() {
        saveFinding("GHSA-NEW-ONE")

        page("/projects/${project.id}?severity=nope&status=zzz")
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("GHSA-NEW-ONE")))
    }

    @Test
    @DisplayName("조건에 맞는 항목이 없으면 안내 문구를 보여준다")
    fun no_match_shows_message() {
        saveFinding("GHSA-NEW-ONE", Severity.HIGH)

        page("/projects/${project.id}?severity=LOW")
            .andExpect(content().string(containsString("조건에 맞는 취약점이 없습니다.")))
    }

    @Test
    @DisplayName("상태 선택 칸은 고르면 바로 저장되고 자바스크립트가 없으면 버튼을 쓴다")
    fun status_select_saves_on_change_with_a_noscript_fallback() {
        saveFinding("GHSA-1")

        page()
            .andExpect(content().string(containsString("onchange=\"this.form.submit()\"")))
            .andExpect(content().string(containsString("<noscript>")))
    }

    @Test
    @DisplayName("상태를 바꾸면 저장하고 변경 이력을 남기고 그 줄로 돌아온다")
    fun status_change_saves_records_history_and_returns_to_the_row() {
        val finding = saveFinding("GHSA-1")

        mockMvc.perform(post("/projects/${project.id}/findings/${finding.id}/status").param("status", "RESOLVED"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/projects/${project.id}#f${finding.id}"))

        assertThat(statusOf(finding.id!!)).isEqualTo("RESOLVED")
        assertThat(count("finding_status_history")).isEqualTo(1)
    }

    @Test
    @DisplayName("상태를 바꾼 뒤에도 필터가 유지된다")
    fun status_change_keeps_the_filters() {
        val finding = saveFinding("GHSA-1")

        mockMvc.perform(
            post("/projects/${project.id}/findings/${finding.id}/status")
                .param("status", "ACKNOWLEDGED")
                .param("filterSeverity", "HIGH")
                .param("filterStatus", "open")
        )
            .andExpect(redirectedUrl("/projects/${project.id}?severity=HIGH&status=open#f${finding.id}"))
    }

    @Test
    @DisplayName("다른 프로젝트의 항목은 이 주소로 바꿀 수 없다")
    fun finding_of_another_project_cannot_be_changed() {
        val other = projectRepository.save(Project("다른", "other.example.com"))
        val otherFinding = saveFinding("GHSA-OTHER", owner = other)

        mockMvc.perform(post("/projects/${project.id}/findings/${otherFinding.id}/status").param("status", "RESOLVED"))
            .andExpect(status().isNotFound)
            .andExpect(view().name("error"))

        assertThat(statusOf(otherFinding.id!!)).isEqualTo("NEW")
        assertThat(count("finding_status_history")).isEqualTo(0)
    }

    @Test
    @DisplayName("잘못된 상태 값은 400 오류 화면을 보여준다")
    fun invalid_status_value_is_400() {
        val finding = saveFinding("GHSA-1")

        mockMvc.perform(post("/projects/${project.id}/findings/${finding.id}/status").param("status", "nope"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(containsString("올바르지 않은 상태입니다.")))
    }

    @Test
    @DisplayName("의존성을 다시 가져오면 목록이 바뀌고 상세 화면으로 돌아온다")
    fun reimport_replaces_the_dependencies() {
        saveFinding("GHSA-1", packageName = "lodash")

        mockMvc.perform(post("/projects/${project.id}/dependencies").param("lockfile", lockfile))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/projects/${project.id}"))

        val names = jdbcTemplate.queryForList("select name from dependency where project_id = ?", String::class.java, project.id)
        assertThat(names).containsExactly("express")
    }

    @Test
    @DisplayName("다시 가져오기가 실패해도 화면은 안내 문구와 함께 다시 나오고 목록은 그대로다")
    fun failed_reimport_shows_the_message_and_changes_nothing() {
        saveFinding("GHSA-1", packageName = "lodash")

        mockMvc.perform(post("/projects/${project.id}/dependencies").param("lockfile", "not json"))
            .andExpect(status().isOk)
            .andExpect(view().name("project-detail"))
            .andExpect(content().string(containsString("지원하지 않는 형식입니다. package-lock.json 내용을 붙여넣어 주세요.")))
            .andExpect(content().string(containsString("GHSA-1")))

        val names = jdbcTemplate.queryForList("select name from dependency where project_id = ?", String::class.java, project.id)
        assertThat(names).containsExactly("lodash")
    }

    @Test
    @DisplayName("비어 있는 잠금 파일은 안내 문구를 보여준다")
    fun blank_reimport_shows_the_message() {
        mockMvc.perform(post("/projects/${project.id}/dependencies").param("lockfile", "   "))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("의존성 목록을 붙여넣어 주세요.")))
    }

    @Test
    @DisplayName("다시 가져오기 폼 아래에 이전 항목이 지워진다는 안내가 있다")
    fun reimport_form_warns_that_old_findings_are_removed() {
        page().andExpect(content().string(containsString("이전 항목과 상태 이력은 함께 지워집니다")))
    }

    @Test
    @DisplayName("웹훅을 저장하면 설정됨으로 바뀌고 주소는 화면에 나오지 않는다")
    fun saving_the_webhook_marks_it_configured_without_showing_it() {
        mockMvc.perform(post("/projects/${project.id}/notification").param("webhookUrl", webhook))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/projects/${project.id}"))

        assertThat(jdbcTemplate.queryForObject("select webhook_url from project where id = ?", String::class.java, project.id)).isEqualTo(webhook)
        page()
            .andExpect(content().string(containsString("디스코드 알림 설정됨")))
            .andExpect(content().string(not(containsString("topsecret"))))
    }

    @Test
    @DisplayName("틀린 웹훅 주소는 저장하지 않고 안내 문구만 보여주며 입력한 주소를 되돌려 그리지 않는다")
    fun invalid_webhook_is_rejected_without_echoing_it() {
        mockMvc.perform(post("/projects/${project.id}/notification").param("webhookUrl", "https://evil.example.com/topsecret"))
            .andExpect(status().isOk)
            .andExpect(view().name("project-detail"))
            .andExpect(content().string(containsString("디스코드 웹훅 주소를 확인해주세요.")))
            .andExpect(content().string(not(containsString("topsecret"))))

        assertThat(jdbcTemplate.queryForObject("select webhook_url from project where id = ?", String::class.java, project.id)).isNull()
    }

    @Test
    @DisplayName("없는 프로젝트에 가져오기나 웹훅을 보내면 404 오류 화면을 보여준다")
    fun unknown_project_is_404_for_posts() {
        mockMvc.perform(post("/projects/999999/dependencies").param("lockfile", lockfile))
            .andExpect(status().isNotFound)
        mockMvc.perform(post("/projects/999999/notification").param("webhookUrl", webhook))
            .andExpect(status().isNotFound)
    }
}
