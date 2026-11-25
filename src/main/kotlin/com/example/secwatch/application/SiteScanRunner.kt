package com.example.secwatch.application

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.project.ProjectManagement
import com.example.secwatch.project.SiteCredentials
import com.example.secwatch.sitescan.SiteScanFailedException
import com.example.secwatch.sitescan.SiteScanRecorder
import com.example.secwatch.sitescan.SiteScanTarget
import com.example.secwatch.sitescan.SiteScanTargets
import com.example.secwatch.sitescan.SiteScanner
import com.example.secwatch.sitescan.ZapReportParser
import java.util.concurrent.Executor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Component

@Component
class SiteScanRunner(
    private val projectManagement: ProjectManagement,
    private val siteScanTargets: SiteScanTargets,
    private val siteScanRecorder: SiteScanRecorder,
    private val siteScanner: SiteScanner,
    private val zapReportParser: ZapReportParser,
    @Qualifier("siteScanExecutor") private val siteScanExecutor: Executor,
) {
    private val log = LoggerFactory.getLogger(SiteScanRunner::class.java)

    /**
     * 점검은 1분쯤 걸려서 기다리지 않고 번호만 돌려준다. 결과는 끝난 뒤 점검 기록에서 본다.
     * 인증 정보는 이번 점검에만 쓰고 저장하지 않는다.
     */
    fun request(projectId: Long, credentials: SiteCredentials? = null): Long {
        val project = projectManagement.get(projectId)
        val target = SiteScanTarget(siteScanTargets.urlFor(project.domain, project.siteScanPath), credentials)
        val scanId = siteScanRecorder.start(projectId).id!!
        try {
            siteScanExecutor.execute { run(scanId, target) }
        } catch (e: TaskRejectedException) {
            siteScanRecorder.fail(scanId, "점검을 시작하지 못했습니다.")
            throw SecwatchException(ErrorCode.SCAN_IN_PROGRESS)
        }
        return scanId
    }

    private fun run(scanId: Long, target: SiteScanTarget) {
        try {
            val rawJson = siteScanner.scan(target)
            siteScanRecorder.succeed(scanId, rawJson, zapReportParser.parse(rawJson))
        } catch (e: SiteScanFailedException) {
            siteScanRecorder.fail(scanId, e.message ?: UNEXPECTED)
        } catch (e: Exception) {
            log.error("사이트 점검 중 오류가 발생했습니다. scanId={}", scanId, e)
            siteScanRecorder.fail(scanId, UNEXPECTED)
        }
    }

    private companion object {
        const val UNEXPECTED = "점검 중 오류가 발생했습니다."
    }
}
