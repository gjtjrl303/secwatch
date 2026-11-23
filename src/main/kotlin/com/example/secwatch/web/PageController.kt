package com.example.secwatch.web

import com.example.secwatch.application.ProjectSetup
import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.DependencyImporter
import com.example.secwatch.finding.Finding
import com.example.secwatch.finding.FindingService
import com.example.secwatch.finding.FindingStatus
import com.example.secwatch.finding.FixAdvice
import com.example.secwatch.finding.VulnerabilityTypeReader
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectManagement
import java.time.LocalDateTime
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.util.UriComponentsBuilder

data class SeverityBadge(val severity: String, val text: String)

data class Option(val value: String, val label: String)

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
    val id: Long,
    val severity: String,
    val severityLabel: String,
    val packageName: String,
    val version: String,
    val summary: String,
    val referenceUrl: String,
    val vulnerabilityId: String,
    val fixedVersions: List<String>,
    val typeLabel: String,
    val typeDescription: String,
    val fixAdvice: String,
    val status: String,
    val statusLabel: String,
)

@Controller
class PageController(
    private val projectManagement: ProjectManagement,
    private val findingService: FindingService,
    private val projectSetup: ProjectSetup,
    private val dependencyImporter: DependencyImporter,
    private val vulnerabilityTypeReader: VulnerabilityTypeReader,
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
    fun detail(
        @PathVariable id: Long,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) status: String?,
        model: Model,
    ): String = renderDetail(id, model, null, null, parseSeverityFilter(severity), normalizeStatusFilter(status))

    @PostMapping("/projects/{id}/dependencies")
    fun reimport(@PathVariable id: Long, @RequestParam(defaultValue = "") lockfile: String, model: Model): String = try {
        dependencyImporter.import(id, lockfile)
        "redirect:/projects/$id"
    } catch (e: SecwatchException) {
        renderDetail(id, model, e.message, null, null, ALL)
    }

    /** 실패해서 다시 그릴 때 입력한 웹훅 주소는 되돌려 담지 않는다. */
    @PostMapping("/projects/{id}/notification")
    fun changeWebhook(@PathVariable id: Long, @RequestParam(defaultValue = "") webhookUrl: String, model: Model): String = try {
        projectManagement.changeWebhook(id, webhookUrl)
        "redirect:/projects/$id"
    } catch (e: SecwatchException) {
        renderDetail(id, model, null, e.message, null, ALL)
    }

    @PostMapping("/projects/{id}/findings/{findingId}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @PathVariable findingId: Long,
        @RequestParam status: String,
        @RequestParam(required = false) filterSeverity: String?,
        @RequestParam(required = false) filterStatus: String?,
    ): String {
        val target = FindingStatus.entries.firstOrNull { candidate -> candidate.name == status.uppercase() }
            ?: throw SecwatchException(ErrorCode.INVALID_STATUS)
        findingService.changeStatus(findingId, target, id)

        val back = UriComponentsBuilder.fromPath("/projects/$id")
        parseSeverityFilter(filterSeverity)?.let { severity -> back.queryParam("severity", severity.name) }
        normalizeStatusFilter(filterStatus).takeIf { normalized -> normalized != ALL }?.let { normalized -> back.queryParam("status", normalized) }
        return "redirect:" + back.fragment("f$findingId").build().encode().toUriString()
    }

    private fun renderDetail(id: Long, model: Model, importError: String?, webhookError: String?, severity: Severity?, status: String): String {
        val project = projectManagement.get(id)
        val all = findingService.list(id, null, null).map { finding -> finding.toRow() }
        val open = all.filter { row -> row.status in OPEN_STATUS_NAMES }
        val shown = all
            .filter { row -> severity == null || row.severity == severity.name }
            .filter { row -> matchesStatus(row.status, status) }

        model.addAttribute("project", project.toDetailView())
        model.addAttribute("groups", FindingGrouping.group(shown))
        model.addAttribute("importError", importError)
        model.addAttribute("webhookError", webhookError)
        model.addAttribute("openTotal", open.size)
        model.addAttribute("totalAll", all.size)
        model.addAttribute("shownTotal", shown.size)
        model.addAttribute(
            "badges",
            Severity.entries.mapNotNull { candidate ->
                open.count { row -> row.severity == candidate.name }.takeIf { total -> total > 0 }
                    ?.let { total -> SeverityBadge(candidate.name, "${candidate.label} $total") }
            },
        )
        model.addAttribute("filterSeverity", severity?.name ?: ALL)
        model.addAttribute("filterStatus", status)
        model.addAttribute("severityOptions", listOf(Option(ALL, "전체")) + Severity.entries.map { candidate -> Option(candidate.name, candidate.label) })
        model.addAttribute(
            "statusOptions",
            listOf(Option(ALL, "전체"), Option(OPEN, "열린 것(새로 발견 또는 확인함)")) +
                FindingStatus.entries.map { candidate -> Option(candidate.name, candidate.label) },
        )
        model.addAttribute("statuses", FindingStatus.entries.map { candidate -> Option(candidate.name, candidate.label) })
        return "project-detail"
    }

    private fun matchesStatus(findingStatus: String, filter: String) = when (filter) {
        ALL -> true
        OPEN -> findingStatus in OPEN_STATUS_NAMES
        else -> findingStatus == filter
    }

    private fun parseSeverityFilter(value: String?): Severity? =
        Severity.entries.firstOrNull { candidate -> candidate.name == value?.uppercase() }

    private fun normalizeStatusFilter(value: String?): String = when {
        value == null -> ALL
        value.lowercase() == OPEN -> OPEN
        else -> FindingStatus.entries.firstOrNull { candidate -> candidate.name == value.uppercase() }?.name ?: ALL
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

    private fun Finding.toRow(): FindingRow {
        val fixed = fixedVersions.split(",").filter { version -> version.isNotBlank() }
        val type = vulnerabilityTypeReader.read(rawJson)
        return FindingRow(
            id = id!!,
            severity = severity.name,
            severityLabel = severity.label,
            packageName = dependency.name,
            version = dependency.version,
            summary = summary,
            referenceUrl = referenceUrl,
            vulnerabilityId = primaryVulnerabilityId,
            fixedVersions = fixed,
            typeLabel = type.label,
            typeDescription = type.description,
            fixAdvice = FixAdvice.text(dependency.version, fixed),
            status = status.name,
            statusLabel = status.label,
        )
    }

    private companion object {
        const val ALL = "all"
        const val OPEN = "open"
        val OPEN_STATUS_NAMES = FindingStatus.OPEN.map { status -> status.name }.toSet()
    }
}
