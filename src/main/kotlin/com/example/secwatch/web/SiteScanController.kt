package com.example.secwatch.web

import com.example.secwatch.application.SiteScanRunner
import com.example.secwatch.common.ApiSuccessResponse
import com.example.secwatch.project.SiteScanSettings
import com.example.secwatch.sitescan.SiteFinding
import com.example.secwatch.sitescan.SiteScanSummary
import com.example.secwatch.sitescan.SiteScanRecorder
import java.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class SiteScanRequest(val username: String = "", val password: String = "") {
    override fun toString() = "SiteScanRequest(username=$username, password=******)"
}

data class SiteScanRequested(val scanId: Long)

data class SiteScanResponse(
    val id: Long,
    val scanType: String,
    val status: String,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
    val errorMessage: String?,
)

data class SiteFindingResponse(
    val id: Long,
    val alertRef: String,
    val name: String,
    val risk: String,
    val description: String,
    val solution: String,
    val references: List<String>,
    val cweId: Int?,
    val instanceCount: Int,
    val sampleUrls: List<String>,
    val status: String,
    val firstSeenAt: LocalDateTime,
    val lastSeenAt: LocalDateTime,
)

@RestController
@RequestMapping("/api/projects/{id}")
class SiteScanController(
    private val siteScanRunner: SiteScanRunner,
    private val siteScanRecorder: SiteScanRecorder,
) {
    @PostMapping("/site-scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(@PathVariable id: Long, @RequestBody(required = false) body: SiteScanRequest?): ApiSuccessResponse<SiteScanRequested> {
        val credentials = body?.let { SiteScanSettings.optionalCredentials(it.username, it.password) }
        return ApiSuccessResponse(SiteScanRequested(siteScanRunner.request(id, credentials)))
    }

    @GetMapping("/site-scans")
    fun scans(@PathVariable id: Long): ApiSuccessResponse<List<SiteScanResponse>> =
        ApiSuccessResponse(siteScanRecorder.scans(id).map { scan -> scan.toResponse() })

    @GetMapping("/site-findings")
    fun findings(@PathVariable id: Long): ApiSuccessResponse<List<SiteFindingResponse>> =
        ApiSuccessResponse(siteScanRecorder.findings(id).map { finding -> finding.toResponse() })

    private fun SiteScanSummary.toResponse() = SiteScanResponse(id, scanType.name, status.name, startedAt, finishedAt, errorMessage)

    private fun SiteFinding.toResponse() = SiteFindingResponse(
        id = id!!,
        alertRef = alertRef,
        name = name,
        risk = risk.name,
        description = description,
        solution = solution,
        references = reference.lines().filter { it.isNotBlank() },
        cweId = cweId,
        instanceCount = instanceCount,
        sampleUrls = sampleUrls.lines().filter { it.isNotBlank() },
        status = status.name,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
    )
}
