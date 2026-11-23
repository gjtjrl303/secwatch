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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@AutoConfigureMockMvc
@DisplayName("웹 화면")
class PageControllerTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository
    @Autowired lateinit var findingRepository: FindingRepository

    private val lockfile = """{"lockfileVersion":3,"packages":{"":{},"node_modules/lodash":{"version":"4.17.20"}}}"""
    private val webhook = "https://discord.com/api/webhooks/1/topsecret"

    private fun saveProject(name: String = "모꼬지", domain: String = "mokkoji.example.com", webhookUrl: String? = null): Project {
        val project = Project(name, domain)
        if (webhookUrl != null) project.changeWebhook(webhookUrl)
        return projectRepository.save(project)
    }

    private fun saveFinding(
        project: Project,
        vulnerabilityId: String,
        severity: Severity = Severity.HIGH,
        status: FindingStatus = FindingStatus.NEW,
        summary: String = "summary of $vulnerabilityId",
        packageName: String = "lodash",
        rawJson: String = "{}",
    ): Finding {
        val dependency = dependencyRepository.findByProjectId(project.id!!).firstOrNull { saved -> saved.name == packageName }
            ?: dependencyRepository.save(Dependency(project, "npm", packageName, "4.17.20"))
        val finding = Finding(
            project = project,
            dependency = dependency,
            primaryVulnerabilityId = vulnerabilityId,
            summary = summary,
            severity = severity,
            fixedVersions = "4.17.21",
            referenceUrl = "https://osv.dev/vulnerability/$vulnerabilityId",
            rawJson = rawJson,
            firstSeenAt = LocalDateTime.now(),
        )
        if (status != FindingStatus.NEW) finding.changeStatus(status)
        return findingRepository.save(finding)
    }

    private fun submitNewProject(
        name: String = "juice",
        domain: String = "juice.example.com",
        lockfile: String = this.lockfile,
        webhookUrl: String = "",
    ) = mockMvc.perform(
        post("/projects")
            .param("name", name)
            .param("domain", domain)
            .param("lockfile", lockfile)
            .param("webhookUrl", webhookUrl)
    )

    @Test
    @DisplayName("목록 화면에 프로젝트와 위험도별 열린 항목 개수를 보여준다")
    fun dashboard_lists_projects_with_open_counts() {
        val project = saveProject()
        saveFinding(project, "A", Severity.HIGH)
        saveFinding(project, "B", Severity.HIGH)
        saveFinding(project, "C", Severity.CRITICAL, status = FindingStatus.ACKNOWLEDGED)

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard"))
            .andExpect(content().string(containsString("모꼬지")))
            .andExpect(content().string(containsString("mokkoji.example.com")))
            .andExpect(content().string(containsString("치명 1")))
            .andExpect(content().string(containsString("높음 2")))
    }

    @Test
    @DisplayName("해결함과 해당 없음 항목은 열린 항목 개수에 넣지 않는다")
    fun dashboard_counts_only_open_findings() {
        val project = saveProject()
        saveFinding(project, "A", Severity.HIGH, FindingStatus.RESOLVED)
        saveFinding(project, "B", Severity.HIGH, FindingStatus.NOT_APPLICABLE)

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("높음 "))))
            .andExpect(content().string(containsString("없음")))
    }

    @Test
    @DisplayName("프로젝트가 없으면 안내 문구를 보여준다")
    fun dashboard_without_projects_shows_empty_message() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("등록된 프로젝트가 없습니다.")))
    }

    @Test
    @DisplayName("프로젝트 이름에 HTML을 넣어도 글자로만 나온다")
    fun project_name_is_escaped_on_dashboard_and_detail() {
        val project = saveProject(name = "<script>alert(1)</script>")

        mockMvc.perform(get("/"))
            .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
        mockMvc.perform(get("/projects/${project.id}"))
            .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
    }

    @Test
    @DisplayName("OSV에서 온 요약에 HTML이 있어도 글자로만 나온다")
    fun finding_summary_is_escaped_on_detail() {
        val project = saveProject()
        saveFinding(project, "GHSA-1", summary = "<img src=x onerror=alert(1)>")

        mockMvc.perform(get("/projects/${project.id}"))
            .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")))
            .andExpect(content().string(not(containsString("<img src=x"))))
    }

    @Test
    @DisplayName("새 프로젝트 등록 화면이 열린다")
    fun new_project_form_is_shown() {
        mockMvc.perform(get("/projects/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("project-new"))
    }

    @Test
    @DisplayName("등록하면 프로젝트와 웹훅과 의존성이 저장되고 상세 화면으로 이동한다")
    fun registration_creates_everything_and_redirects() {
        submitNewProject(webhookUrl = webhook)
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrlPattern("/projects/*"))

        assertThat(count("project")).isEqualTo(1)
        assertThat(count("dependency")).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("select webhook_url from project", String::class.java)).isEqualTo(webhook)
    }

    @Test
    @DisplayName("등록할 때 의존성 목록을 파일로 올릴 수 있다")
    fun create_with_an_uploaded_file() {
        val file = MockMultipartFile("file", "package-lock.json", "application/json", lockfile.toByteArray())

        mockMvc.perform(multipart("/projects").file(file).param("name", "juice").param("domain", "juice.example.com"))
            .andExpect(status().is3xxRedirection)

        assertThat(dependencyRepository.findAll().map { dependency -> dependency.name }).containsExactly("lodash")
    }

    @Test
    @DisplayName("등록 화면이 세 가지 형식과 파일 올리기를 안내한다")
    fun new_form_explains_the_three_formats() {
        mockMvc.perform(get("/projects/new"))
            .andExpect(content().string(containsString("package-lock.json")))
            .andExpect(content().string(containsString("./gradlew dependencies")))
            .andExpect(content().string(containsString("CycloneDX SBOM")))
            .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
            .andExpect(content().string(containsString("type=\"file\"")))
            .andExpect(content().string(containsString("size > 20971520 ? '파일이 너무 큽니다. 20MB 이하의 파일을 올려 주세요.' : ''")))
    }

    @Test
    @DisplayName("잠금 파일과 웹훅은 비워도 등록할 수 있다")
    fun lockfile_and_webhook_are_optional() {
        submitNewProject(lockfile = "", webhookUrl = "")
            .andExpect(status().is3xxRedirection)

        assertThat(count("project")).isEqualTo(1)
        assertThat(count("dependency")).isEqualTo(0)
    }

    @Test
    @DisplayName("잘못된 도메인이면 안내 문구와 함께 다시 보여주고 아무것도 만들지 않는다")
    fun invalid_domain_rerenders_with_message_and_creates_nothing() {
        submitNewProject(domain = "https://x.com")
            .andExpect(status().isOk)
            .andExpect(view().name("project-new"))
            .andExpect(content().string(containsString("올바른 도메인을 입력해주세요.")))

        assertThat(count("project")).isEqualTo(0)
    }

    @Test
    @DisplayName("잠금 파일 형식이 틀리면 프로젝트 등록까지 통째로 되돌린다")
    fun unsupported_lockfile_rolls_back_the_whole_registration() {
        submitNewProject(lockfile = "not json", webhookUrl = webhook)
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("지원하지 않는 형식입니다. package-lock.json, Gradle dependencies 출력, CycloneDX SBOM 중 하나를 넣어 주세요.")))

        assertThat(count("project")).isEqualTo(0)
        assertThat(count("dependency")).isEqualTo(0)
    }

    @Test
    @DisplayName("이미 등록된 도메인이면 안내 문구를 보여준다")
    fun duplicate_domain_shows_message() {
        submitNewProject().andExpect(status().is3xxRedirection)

        submitNewProject()
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("이미 등록된 도메인입니다.")))
    }

    @Test
    @DisplayName("웹훅 주소가 틀리면 안내 문구를 보여주고 아무것도 만들지 않는다")
    fun invalid_webhook_shows_message_and_creates_nothing() {
        submitNewProject(webhookUrl = "https://evil.example.com/x")
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("디스코드 웹훅 주소를 확인해주세요.")))

        assertThat(count("project")).isEqualTo(0)
    }

    @Test
    @DisplayName("등록이 실패해서 다시 보여줄 때도 웹훅 주소를 화면에 되돌려 그리지 않는다")
    fun failed_registration_does_not_echo_the_webhook() {
        submitNewProject(lockfile = "not json", webhookUrl = webhook)
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("topsecret"))))
        submitNewProject(webhookUrl = "https://evil.example.com/topsecret")
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("topsecret"))))
    }

    @Test
    @DisplayName("이름은 다시 보여주되 HTML은 글자로만 나온다")
    fun failed_registration_keeps_the_name_escaped() {
        submitNewProject(name = "<b>x</b>", domain = "https://x.com")
            .andExpect(content().string(containsString("&lt;b&gt;x&lt;/b&gt;")))
            .andExpect(content().string(not(containsString("<b>x</b>"))))
    }

    @Test
    @DisplayName("상세 화면에 항목이 위험도 순서로 나오고 미분류도 표시한다")
    fun detail_shows_findings_by_severity_with_unclassified_label() {
        val project = saveProject()
        saveFinding(project, "GHSA-LOW", Severity.UNCLASSIFIED, packageName = "a")
        saveFinding(project, "GHSA-TOP", Severity.CRITICAL, packageName = "b")

        val html = mockMvc.perform(get("/projects/${project.id}"))
            .andExpect(status().isOk)
            .andExpect(view().name("project-detail"))
            .andExpect(content().string(containsString("미분류")))
            .andReturn().response.contentAsString

        assertThat(html.indexOf("GHSA-TOP")).isLessThan(html.indexOf("GHSA-LOW"))
    }

    @Test
    @DisplayName("상세 화면의 항목에 한국어 유형 설명과 고치는 방법이 나온다")
    fun detail_shows_type_description_and_fix_advice() {
        val project = saveProject()
        saveFinding(project, "GHSA-DOS", rawJson = """{"database_specific":{"cwe_ids":["CWE-400"]}}""")
        saveFinding(project, "GHSA-NOTYPE", packageName = "other")

        mockMvc.perform(get("/projects/${project.id}"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("서비스 멈춤 위험")))
            .andExpect(content().string(containsString("분류되지 않은 유형")))
            .andExpect(content().string(containsString("4.17.21 이상으로 올립니다.")))
            .andExpect(content().string(containsString("summary of GHSA-DOS")))
            .andExpect(content().string(containsString("https://osv.dev/vulnerability/GHSA-DOS")))
    }

    @Test
    @DisplayName("상세 화면에 위험도 기준 설명이 있다")
    fun detail_shows_severity_guide() {
        val project = saveProject()

        mockMvc.perform(get("/projects/${project.id}"))
            .andExpect(content().string(containsString("위험도 기준")))
    }

    @Test
    @DisplayName("상세 화면에 마지막 점검과 알림 오류가 나온다")
    fun detail_shows_last_check_and_errors() {
        val project = saveProject()
        mockMvc.perform(get("/projects/${project.id}"))
            .andExpect(content().string(containsString("아직 없음")))

        jdbcTemplate.update(
            "update project set last_checked_at = now(6), last_check_error = '점검 실패 문구', last_notify_error = '알림 실패 문구' where id = ?",
            project.id,
        )

        mockMvc.perform(get("/projects/${project.id}"))
            .andExpect(content().string(not(containsString("아직 없음"))))
            .andExpect(content().string(containsString("점검 실패 문구")))
            .andExpect(content().string(containsString("알림 실패 문구")))
    }

    @Test
    @DisplayName("상세 화면은 웹훅이 설정됐는지만 알려주고 주소는 절대 그리지 않는다")
    fun detail_never_renders_webhook_url() {
        val withWebhook = saveProject(webhookUrl = webhook)
        val withoutWebhook = saveProject(name = "다른", domain = "other.example.com")

        mockMvc.perform(get("/projects/${withWebhook.id}"))
            .andExpect(content().string(containsString("디스코드 알림 설정됨")))
            .andExpect(content().string(not(containsString("topsecret"))))
            .andExpect(content().string(not(containsString(webhook))))
            .andExpect(content().string(not(containsString("webhooks/1/"))))
        mockMvc.perform(get("/projects/${withoutWebhook.id}"))
            .andExpect(content().string(containsString("디스코드 알림 미설정")))
        mockMvc.perform(get("/"))
            .andExpect(content().string(not(containsString("topsecret"))))
    }

    @Test
    @DisplayName("없는 프로젝트의 상세 화면은 404 오류 화면을 보여준다")
    fun detail_for_unknown_project_is_404() {
        mockMvc.perform(get("/projects/999999"))
            .andExpect(status().isNotFound)
            .andExpect(view().name("error"))
            .andExpect(content().string(containsString("프로젝트를 찾을 수 없습니다.")))
    }
}
