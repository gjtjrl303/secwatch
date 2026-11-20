package com.example.secwatch.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("패키지별 묶기")
class FindingGroupingTest {
    private var nextId = 1L

    private fun row(packageName: String, version: String, severity: String, label: String = severity) = FindingRow(
        id = nextId++,
        severity = severity,
        severityLabel = label,
        packageName = packageName,
        version = version,
        summary = "s",
        referenceUrl = "https://osv.dev/vulnerability/x",
        vulnerabilityId = "GHSA-x",
        fixedVersions = emptyList(),
        status = "NEW",
        statusLabel = "새로 발견",
    )

    @Test
    @DisplayName("패키지와 버전별로 묶는다")
    fun groups_by_package_and_version() {
        val groups = FindingGrouping.group(listOf(row("a", "1", "LOW"), row("a", "2", "LOW"), row("a", "1", "HIGH")))

        assertThat(groups.map { group -> "${group.packageName}@${group.version}:${group.count}" })
            .containsExactlyInAnyOrder("a@1:2", "a@2:1")
    }

    @Test
    @DisplayName("묶음은 가장 높은 위험도를 대표로 보여준다")
    fun group_reports_its_highest_severity() {
        val group = FindingGrouping.group(listOf(row("a", "1", "LOW"), row("a", "1", "CRITICAL", "치명"))).single()

        assertThat(group.highestSeverity).isEqualTo("CRITICAL")
        assertThat(group.highestLabel).isEqualTo("치명")
    }

    @Test
    @DisplayName("위험도가 높은 순, 건수가 많은 순, 이름순으로 정렬한다")
    fun groups_are_sorted_by_severity_then_count_then_name() {
        val groups = FindingGrouping.group(
            listOf(
                row("low-many", "1", "LOW"), row("low-many", "1", "LOW"),
                row("high-one", "1", "HIGH"),
                row("b-high-two", "1", "HIGH"), row("b-high-two", "1", "HIGH"),
                row("a-high-two", "1", "HIGH"), row("a-high-two", "1", "HIGH"),
            )
        )

        assertThat(groups.map { group -> group.packageName }).containsExactly("a-high-two", "b-high-two", "high-one", "low-many")
    }

    @Test
    @DisplayName("항목이 없으면 묶음도 없다")
    fun empty_input_gives_no_groups() {
        assertThat(FindingGrouping.group(emptyList())).isEmpty()
    }
}
