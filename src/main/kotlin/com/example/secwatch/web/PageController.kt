package com.example.secwatch.web

import com.example.secwatch.application.ProjectSetup
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.common.Severity
import com.example.secwatch.finding.Finding
import com.example.secwatch.finding.FindingService
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectManagement
import java.time.LocalDateTime
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

data class SeverityBadge(val severity: String, val text: String)

data class DashboardRow(val id: Long, val name: String, val domain: String, val counts: List<SeverityBadge>)

/** 웹훅 주소는 비밀값이라 화면 모델에 일부러 넣지 않고, 설정 여부만 담는다. */
data class ProjectDetailView(
    val id: Long,
    val name: String,
    val domain: String,
    val webhookConfigured: Boolean,
    val lastCheckedAt: LocalDateTime?,
    val lastCheckError: String?,
    val lastNotifyError: String?,
)

data class FindingRow(
    val severity: String,
    val severityLabel: String,
    val packageName: String,
    val version: String,
    val summary: String,
    val referenceUrl: String,
    val vulnerabilityId: String,
    val fixedVersions: List<String>,
    val statusLabel: String,
)

@Controller
class PageController(
    private val projectManagement: ProjectManagement,
    private val findingService: FindingService,
    private val projectSetup: ProjectSetup,
) {
    @GetMapping("/")
    fun dashboard(model: Model): String {
        val countsByProject = findingService.openSeverityCounts()
        model.addAttribute("rows", projectManagement.list().map { project -> project.toDashboardRow(countsByProject[project.id].orEmpty()) })
        return "dashboard"
    }

    @GetMapping("/projects/new")
    fun newForm(): String = "project-new"

    /** 실패해서 다시 그릴 때 웹훅 주소는 되돌려 담지 않는다. 비밀값이 화면에 남지 않게 하기 위해서다. */
    @PostMapping("/projects")
    fun create(
        @RequestParam name: String,
        @RequestParam domain: String,
        @RequestParam(defaultValue = "") lockfile: String,
        @RequestParam(defaultValue = "") webhookUrl: String,
        model: Model,
    ): String = try {
        "redirect:/projects/${projectSetup.create(name, domain, webhookUrl, lockfile)}"
    } catch (e: SecwatchException) {
        model.addAttribute("error", e.message)
        model.addAttribute("name", name)
        model.addAttribute("domain", domain)
        model.addAttribute("lockfile", lockfile)
        "project-new"
    }

    @GetMapping("/projects/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val project = projectManagement.get(id)
        model.addAttribute("project", project.toDetailView())
        model.addAttribute("findings", findingService.list(id, null, null).map { finding -> finding.toRow() })
        return "project-detail"
    }

    private fun Project.toDashboardRow(countBySeverity: Map<Severity, Long>) = DashboardRow(
        id = id!!,
        name = name,
        domain = domain,
        counts = Severity.entries
            .filter { severity -> severity in countBySeverity }
            .map { severity -> SeverityBadge(severity.name, "${severity.label} ${countBySeverity.getValue(severity)}") },
    )

    private fun Project.toDetailView() = ProjectDetailView(
        id = id!!,
        name = name,
        domain = domain,
        webhookConfigured = !webhookUrl.isNullOrBlank(),
        lastCheckedAt = lastCheckedAt,
        lastCheckError = lastCheckError,
        lastNotifyError = lastNotifyError,
    )

    private fun Finding.toRow() = FindingRow(
        severity = severity.name,
        severityLabel = severity.label,
        packageName = dependency.name,
        version = dependency.version,
        summary = summary,
        referenceUrl = referenceUrl,
        vulnerabilityId = primaryVulnerabilityId,
        fixedVersions = fixedVersions.split(",").filter { version -> version.isNotBlank() },
        statusLabel = status.label,
    )
}
