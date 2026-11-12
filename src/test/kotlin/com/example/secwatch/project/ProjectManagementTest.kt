package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@DisplayName("프로젝트 조회와 알림 설정")
class ProjectManagementTest : MySqlIntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var management: ProjectManagement

    private var projectId = 0L

    @BeforeEach
    fun seed() {
        jdbc.update("delete from project")
        jdbc.update("insert into project(name, domain) values ('t', 'm.example.com')")
        projectId = jdbc.queryForObject("select id from project", Long::class.java)!!
    }

    private fun storedWebhook() =
        jdbc.queryForObject("select webhook_url from project where id = ?", String::class.java, projectId)

    @Test
    @DisplayName("등록된 프로젝트를 모두 돌려준다")
    fun lists_all_projects() {
        assertThat(management.list()).extracting<String> { it.domain }.containsExactly("m.example.com")
    }

    @Test
    @DisplayName("id로 프로젝트 하나를 찾는다")
    fun gets_project_by_id() {
        assertThat(management.get(projectId).domain).isEqualTo("m.example.com")
    }

    @Test
    @DisplayName("없는 id로 찾으면 프로젝트를 찾을 수 없다는 오류가 난다")
    fun get_missing_project_fails() {
        assertThatThrownBy { management.get(projectId + 999) }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.NOT_FOUND_PROJECT) }
            .hasMessage("프로젝트를 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("올바른 디스코드 주소는 저장한다")
    fun changes_webhook() {
        management.changeWebhook(projectId, "https://discord.com/api/webhooks/1/a")
        assertThat(storedWebhook()).isEqualTo("https://discord.com/api/webhooks/1/a")
    }

    @Test
    @DisplayName("디스코드 주소가 아니면 거부하고 저장하지 않는다")
    fun rejects_invalid_webhook() {
        assertThatThrownBy { management.changeWebhook(projectId, "https://evil.example.com/api/webhooks/1/a") }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_WEBHOOK_URL) }
        assertThat(storedWebhook()).isNull()
    }

    @Test
    @DisplayName("없는 프로젝트의 알림을 바꾸면 프로젝트를 찾을 수 없다는 오류가 난다")
    fun change_webhook_of_missing_project_fails() {
        assertThatThrownBy { management.changeWebhook(projectId + 999, "https://discord.com/api/webhooks/1/a") }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.NOT_FOUND_PROJECT) }
    }
}
