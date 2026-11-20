package com.example.secwatch.finding

import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.DbIntegrationTest
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("발견 항목 상태 변경의 동시성")
class FindingStatusConcurrencyTest : DbIntegrationTest() {
    @Autowired lateinit var findingService: FindingService
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository
    @Autowired lateinit var findingRepository: FindingRepository

    private var findingId = 0L

    @BeforeEach
    fun seed() {
        val project = projectRepository.save(Project("s", "s.example.com"))
        val dependency = dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20"))
        findingId = findingRepository.save(
            Finding(project, dependency, "A", "요약", Severity.HIGH, "4.17.21", "https://osv.dev/vulnerability/A", "{}", LocalDateTime.now())
        ).id!!
    }

    private fun historyRows() =
        jdbcTemplate.queryForList("select from_status, to_status from finding_status_history order by id")

    @Test
    @DisplayName("다른 요청이 같은 상태로 바꾸는 중에 들어온 요청은 최신 상태를 보고 이력을 중복으로 남기지 않는다")
    fun same_target_status_during_another_change_does_not_duplicate_history() {
        val result = blockedWhileFindingIsLocked(
            findingId,
            uncommitted = {
                jdbcTemplate.update("update finding set status = 'ACKNOWLEDGED' where id = ?", findingId)
                jdbcTemplate.update(
                    "insert into finding_status_history (finding_id, from_status, to_status, changed_at) values (?, 'NEW', 'ACKNOWLEDGED', now(6))",
                    findingId,
                )
            },
        ) { findingService.changeStatus(findingId, FindingStatus.ACKNOWLEDGED) }

        assertThat(result.status).isEqualTo(FindingStatus.ACKNOWLEDGED)
        assertThat(historyRows()).hasSize(1)
    }

    @Test
    @DisplayName("다른 요청이 먼저 바꾼 뒤 들어온 요청은 실제 이전 상태를 이력에 남긴다")
    fun history_records_the_actual_previous_status() {
        blockedWhileFindingIsLocked(
            findingId,
            uncommitted = {
                jdbcTemplate.update("update finding set status = 'ACKNOWLEDGED' where id = ?", findingId)
                jdbcTemplate.update(
                    "insert into finding_status_history (finding_id, from_status, to_status, changed_at) values (?, 'NEW', 'ACKNOWLEDGED', now(6))",
                    findingId,
                )
            },
        ) { findingService.changeStatus(findingId, FindingStatus.RESOLVED) }

        val rows = historyRows()
        assertThat(rows.map { row -> row["from_status"] to row["to_status"] })
            .containsExactly("NEW" to "ACKNOWLEDGED", "ACKNOWLEDGED" to "RESOLVED")
    }
}
