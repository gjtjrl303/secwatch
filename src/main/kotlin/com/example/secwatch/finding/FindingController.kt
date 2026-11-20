package com.example.secwatch.finding

import com.example.secwatch.common.ApiSuccessResponse
import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.common.Severity
import java.time.LocalDateTime
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ChangeStatusRequest(val status: String = "")

data class FindingResponse(
    val id: Long,
    val severity: String,
    val status: String,
    val packageName: String,
    val version: String,
    val summary: String,
    val fixedVersions: List<String>,
    val referenceUrl: String,
    val firstSeenAt: LocalDateTime,
)

@RestController
class FindingController(private val findingService: FindingService) {

    @GetMapping("/api/projects/{id}/findings")
    fun list(
        @PathVariable id: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) severity: String?,
    ): ApiSuccessResponse<List<FindingResponse>> {
        val statusFilter = if (status == null) null else parseStatus(status)
        val severityFilter = if (severity == null) null else parseSeverity(severity)
        val findings = findingService.list(id, statusFilter, severityFilter)
        return ApiSuccessResponse(findings.map { finding -> finding.toResponse() })
    }

    @PatchMapping("/api/findings/{id}")
    fun changeStatus(@PathVariable id: Long, @RequestBody request: ChangeStatusRequest): ApiSuccessResponse<FindingResponse> {
        val finding = findingService.changeStatus(id, parseStatus(request.status))
        return ApiSuccessResponse(finding.toResponse())
    }

    private fun parseStatus(value: String): FindingStatus {
        val matched = FindingStatus.entries.firstOrNull { status -> status.name == value.uppercase() }
        return matched ?: throw SecwatchException(ErrorCode.INVALID_STATUS)
    }

    private fun parseSeverity(value: String): Severity {
        val matched = Severity.entries.firstOrNull { severity -> severity.name == value.uppercase() }
        return matched ?: throw SecwatchException(ErrorCode.INVALID_SEVERITY)
    }

    private fun Finding.toResponse() = FindingResponse(
        id = id!!,
        severity = severity.name,
        status = status.name,
        packageName = dependency.name,
        version = dependency.version,
        summary = summary,
        fixedVersions = fixedVersions.split(",").filter { version -> version.isNotBlank() },
        referenceUrl = referenceUrl,
        firstSeenAt = firstSeenAt,
    )
}
