package com.example.secwatch.web

import com.example.secwatch.common.Severity

data class PackageGroup(
    val packageName: String,
    val version: String,
    val highestSeverity: String,
    val highestLabel: String,
    val count: Int,
    val findings: List<FindingRow>,
)

object FindingGrouping {
    /** 패키지와 버전이 같은 항목끼리 묶고, 위험도가 높은 순, 건수가 많은 순, 이름순으로 정렬한다. */
    fun group(findings: List<FindingRow>): List<PackageGroup> =
        findings.groupBy { finding -> finding.packageName to finding.version }
            .map { (key, groupFindings) ->
                val worst = groupFindings.minBy { finding -> rank(finding.severity) }
                PackageGroup(key.first, key.second, worst.severity, worst.severityLabel, groupFindings.size, groupFindings)
            }
            .sortedWith(
                compareBy<PackageGroup> { group -> rank(group.highestSeverity) }
                    .thenByDescending { group -> group.count }
                    .thenBy { group -> group.packageName }
            )

    private fun rank(severity: String) = Severity.valueOf(severity).ordinal
}
