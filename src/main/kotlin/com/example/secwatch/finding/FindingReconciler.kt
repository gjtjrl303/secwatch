package com.example.secwatch.finding

import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.osv.OsvVulnerability
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FindingReconciler(
    private val dependencyRepository: DependencyRepository,
    private val findingRepository: FindingRepository,
    private val findingKeyRepository: FindingKeyRepository,
) {
    private val log = LoggerFactory.getLogger(FindingReconciler::class.java)

    /** id나 별칭이 하나라도 겹치면 같은 취약점으로 본다. GHSA와 CVE가 항목 두 개가 되지 않게 한다. */
    @Transactional
    fun reconcile(dependencyId: Long, vulnerabilities: List<OsvVulnerability>): List<Long> {
        // 점검 중에 다시 가져오기로 의존성이 지워졌으면 기록할 것이 없다
        val dependency = dependencyRepository.findById(dependencyId).orElse(null) ?: return emptyList()

        val createdFindingIds = mutableListOf<Long>()
        for (vulnerability in vulnerabilities) {
            // 저장 열보다 긴 번호는 다시 시도해도 저장할 수 없다. 이 기록만 건너뛰어 점검 전체가 막히지 않게 한다
            if (vulnerability.id.length > MAX_VULNERABILITY_ID_LENGTH) {
                log.warn("번호가 너무 길어 건너뜁니다. dependencyId={}, 길이={}", dependencyId, vulnerability.id.length)
                continue
            }
            val keys = storableKeys(dependencyId, vulnerability)
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

    private fun storableKeys(dependencyId: Long, vulnerability: OsvVulnerability): List<String> {
        val keys = (listOf(vulnerability.id) + vulnerability.aliases).distinct()
        val storableKeys = keys.filter { key -> key.length <= MAX_KEY_LENGTH }
        if (storableKeys.size != keys.size) {
            log.warn("별칭이 너무 길어 뺐습니다. dependencyId={}, 번호={}", dependencyId, vulnerability.id)
        }
        return storableKeys
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
        const val MAX_VULNERABILITY_ID_LENGTH = 100
        const val MAX_KEY_LENGTH = 150
        const val MAX_SUMMARY_LENGTH = 1000
        const val MAX_FIXED_VERSIONS_LENGTH = 1000
    }
}
