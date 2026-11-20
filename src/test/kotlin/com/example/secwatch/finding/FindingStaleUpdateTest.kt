package com.example.secwatch.finding

import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.DbIntegrationTest
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("발견 항목 갱신")
class FindingStaleUpdateTest : DbIntegrationTest() {
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository
    @Autowired lateinit var findingRepository: FindingRepository

    @Test
    @DisplayName("오래된 값으로 읽은 항목의 상태를 바꿔도 바꾸지 않은 컬럼은 되돌리지 않는다")
    fun changing_status_does_not_revert_columns_updated_meanwhile() {
        val project = projectRepository.save(Project("s", "s.example.com"))
        val dependency = dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20"))
        val findingId = findingRepository.save(
            Finding(project, dependency, "A", "원래 요약", Severity.HIGH, "4.17.21", "https://osv.dev/vulnerability/A", "{}", LocalDateTime.now())
        ).id!!

        TransactionTemplate(transactionManager).executeWithoutResult { _ ->
            val finding = findingRepository.findById(findingId).get()
            // 읽은 뒤에 다른 곳에서 요약이 바뀐 상황을 만든다
            jdbcTemplate.update("update finding set summary = '바뀐 요약' where id = ?", findingId)
            finding.changeStatus(FindingStatus.ACKNOWLEDGED)
        }

        val row = jdbcTemplate.queryForMap("select summary, status from finding where id = ?", findingId)
        assertThat(row["status"]).isEqualTo("ACKNOWLEDGED")
        assertThat(row["summary"]).isEqualTo("바뀐 요약")
    }
}
