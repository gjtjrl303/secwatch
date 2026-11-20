package com.example.secwatch.project

import com.example.secwatch.support.DbIntegrationTest
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("프로젝트 갱신")
class ProjectStaleUpdateTest : DbIntegrationTest() {
    @Autowired lateinit var projectRepository: ProjectRepository

    @Test
    @DisplayName("오래된 값으로 읽은 프로젝트를 바꿔도 바꾸지 않은 컬럼은 되돌리지 않는다")
    fun changing_one_column_does_not_revert_columns_updated_meanwhile() {
        val projectId = projectRepository.save(Project("s", "s.example.com")).id!!
        val checkedAt = LocalDateTime.of(2026, 11, 18, 9, 0)

        TransactionTemplate(transactionManager).executeWithoutResult { _ ->
            val project = projectRepository.findById(projectId).get()
            // 읽은 뒤에 다른 점검이 점검 시각을 갱신한 상황을 만든다
            jdbcTemplate.update("update project set last_checked_at = ? where id = ?", checkedAt, projectId)
            project.changeWebhook("https://discord.com/api/webhooks/1/a")
        }

        val row = jdbcTemplate.queryForMap("select last_checked_at, webhook_url from project where id = ?", projectId)
        assertThat(row["webhook_url"]).isEqualTo("https://discord.com/api/webhooks/1/a")
        assertThat(row["last_checked_at"]).isNotNull
    }
}
