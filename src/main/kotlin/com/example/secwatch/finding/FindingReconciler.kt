package com.example.secwatch.finding

import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.osv.OsvVulnerability
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FindingReconciler(
    private val dependencyRepository: DependencyRepository,
    private val findingRepository: FindingRepository,
    private val findingKeyRepository: FindingKeyRepository,
) {
    /** id나 별칭이 하나라도 겹치면 같은 취약점으로 본다. GHSA와 CVE가 항목 두 개가 되지 않게 한다. */
    @Transactional
    fun reconcile(dependencyId: Long, vulnerabilities: List<OsvVulnerability>): List<Long> {
        // 점검 중에 다시 가져오기로 의존성이 지워졌으면 기록할 것이 없다
        val dependency = dependencyRepository.findById(dependencyId).orElse(null) ?: return emptyList()

        val createdFindingIds = mutableListOf<Long>()
        for (vulnerability in vulnerabilities) {
            val keys = (listOf(vulnerability.id) + vulnerability.aliases).distinct()
            val existingKeys = findingKeyRepository.findByDependencyAndVulnerabilityKeyIn(dependency, keys)
            val existingFinding = existingKeys.firstOrNull()?.finding

            val finding: Finding
            if (existingFinding == null) {
                finding = findingRepository.save(newFinding(dependency, vulnerability))
                createdFindingIds.add(finding.id!!)
            } else {
                finding = existingFinding
            }

            val knownKeys = existingKeys.map { key -> key.vulnerabilityKey }.toSet()
            val newKeys = keys.filter { key -> key !in knownKeys }
            findingKeyRepository.saveAll(newKeys.map { key -> FindingKey(finding, dependency, key) })
        }
        return createdFindingIds
    }

    private fun newFinding(dependency: Dependency, vulnerability: OsvVulnerability) = Finding(
        project = dependency.project,
        dependency = dependency,
        primaryVulnerabilityId = vulnerability.id,
        summary = vulnerability.summary.take(MAX_SUMMARY_LENGTH),
        severity = vulnerability.severity,
        fixedVersions = vulnerability.fixedVersions.joinToString(",").take(MAX_FIXED_VERSIONS_LENGTH),
        referenceUrl = vulnerability.referenceUrl,
        rawJson = vulnerability.rawJson,
        firstSeenAt = LocalDateTime.now(),
    )

    private companion object {
        const val MAX_SUMMARY_LENGTH = 1000
        const val MAX_FIXED_VERSIONS_LENGTH = 1000
    }
}
