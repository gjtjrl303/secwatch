package com.example.secwatch.finding

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.osv.OsvUnavailableException
import com.example.secwatch.osv.OsvVulnerability
import com.example.secwatch.osv.VulnerabilitySource
import com.example.secwatch.project.ProjectRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

data class CheckResult(val newFindingIds: List<Long>)

private data class DependencyVulnerabilities(val dependencyId: Long, val vulnerabilities: List<OsvVulnerability>)

@Service
class CheckService(
    private val projectRepository: ProjectRepository,
    private val dependencyRepository: DependencyRepository,
    private val vulnerabilitySource: VulnerabilitySource,
    private val findingReconciler: FindingReconciler,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(CheckService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /** OSV 조회를 먼저 끝내고 프로젝트 행 잠금 안에서 한 번에 저장한다. 실패해도 저장된 것은 바뀌지 않는다. */
    fun check(projectId: Long): CheckResult {
        if (!projectRepository.existsById(projectId)) throw SecwatchException(ErrorCode.NOT_FOUND_PROJECT)

        return try {
            val dependencies = dependencyRepository.findByProjectId(projectId)
            val fetched = fetchVulnerabilities(dependencies)
            persist(projectId, fetched)
        } catch (e: OsvUnavailableException) {
            log.warn("OSV를 사용할 수 없어 점검하지 못했습니다. projectId={}, 원인={}", projectId, e.message)
            recordFailure(projectId)
            CheckResult(emptyList())
        } catch (e: Exception) {
            log.error("점검 중 예상하지 못한 오류가 났습니다. projectId={}", projectId, e)
            recordFailure(projectId)
            CheckResult(emptyList())
        }
    }

    private fun fetchVulnerabilities(dependencies: List<Dependency>): List<DependencyVulnerabilities> {
        val packageReferences = dependencies.map { dependency -> dependency.toPackageReference() }.distinct()
        val idsByPackageReference = vulnerabilitySource.findVulnerabilityIds(packageReferences)

        val detailsById = HashMap<String, OsvVulnerability?>()
        val fetched = mutableListOf<DependencyVulnerabilities>()
        for (dependency in dependencies) {
            val vulnerabilityIds = idsByPackageReference[dependency.toPackageReference()].orEmpty()
            val vulnerabilities = mutableListOf<OsvVulnerability>()
            for (vulnerabilityId in vulnerabilityIds) {
                if (vulnerabilityId !in detailsById) {
                    detailsById[vulnerabilityId] = vulnerabilitySource.getVulnerability(vulnerabilityId)
                }
                val vulnerability = detailsById[vulnerabilityId]
                if (vulnerability != null) vulnerabilities.add(vulnerability)
            }
            fetched.add(DependencyVulnerabilities(dependency.id!!, vulnerabilities))
        }
        return fetched
    }

    private fun persist(projectId: Long, fetched: List<DependencyVulnerabilities>): CheckResult {
        val result = transactionTemplate.execute { _ ->
            val project = projectRepository.findForUpdate(projectId) ?: return@execute CheckResult(emptyList())

            val newFindingIds = mutableListOf<Long>()
            for (item in fetched) {
                newFindingIds.addAll(findingReconciler.reconcile(item.dependencyId, item.vulnerabilities))
            }
            project.markChecked(LocalDateTime.now())
            CheckResult(newFindingIds)
        }
        return result!!
    }

    private fun recordFailure(projectId: Long) {
        transactionTemplate.executeWithoutResult { _ ->
            val project = projectRepository.findById(projectId).orElse(null)
            if (project != null) project.markCheckFailed(CHECK_ERROR)
        }
    }

    companion object {
        const val CHECK_ERROR = "취약점 정보를 불러오지 못했습니다. 다음 주기에 다시 시도합니다."
    }
}
