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
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var projectManagement: ProjectManagement

    private var projectId = 0L

    @BeforeEach
    fun seed() {
        jdbcTemplate.update("delete from project")
        jdbcTemplate.update("insert into project(name, domain) values ('t', 'm.example.com')")
        projectId = jdbcTemplate.queryForObject("select id from project", Long::class.java)!!
    }

    private fun storedWebhook() =
        jdbcTemplate.queryForObject("select webhook_url from project where id = ?", String::class.java, projectId)

    @Test
    @DisplayName("등록된 프로젝트를 모두 돌려준다")
    fun lists_all_projects() {
        assertThat(projectManagement.list()).extracting<String> { it.domain }.containsExactly("m.example.com")
    }

    @Test
    @DisplayName("id로 프로젝트 하나를 찾는다")
    fun gets_project_by_id() {
        assertThat(projectManagement.get(projectId).domain).isEqualTo("m.example.com")
    }

    @Test
    @DisplayName("없는 id로 찾으면 프로젝트를 찾을 수 없다는 오류가 난다")
    fun get_missing_project_fails() {
        assertThatThrownBy { projectManagement.get(projectId + 999) }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.NOT_FOUND_PROJECT) }
            .hasMessage("프로젝트를 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("올바른 디스코드 주소는 저장한다")
    fun changes_webhook() {
        projectManagement.changeWebhook(projectId, "https://discord.com/api/webhooks/1/a")
        assertThat(storedWebhook()).isEqualTo("https://discord.com/api/webhooks/1/a")
    }

    @Test
    @DisplayName("디스코드 주소가 아니면 거부하고 저장하지 않는다")
    fun rejects_invalid_webhook() {
        assertThatThrownBy { projectManagement.changeWebhook(projectId, "https://evil.example.com/api/webhooks/1/a") }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_WEBHOOK_URL) }
        assertThat(storedWebhook()).isNull()
    }

    @Test
    @DisplayName("없는 프로젝트의 알림을 바꾸면 프로젝트를 찾을 수 없다는 오류가 난다")
    fun change_webhook_of_missing_project_fails() {
        assertThatThrownBy { projectManagement.changeWebhook(projectId + 999, "https://discord.com/api/webhooks/1/a") }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.NOT_FOUND_PROJECT) }
    }

    private fun storedSiteScanColumns(): Map<String, Any?> =
        jdbcTemplate.queryForMap("select site_scan_path, site_auth_username, site_auth_password from project where id = ?", projectId)

    @Test
    @DisplayName("사이트 점검 시작 경로를 저장하고, 비우면 지운다")
    fun changes_site_scan_path() {
        projectManagement.changeSiteScanPath(projectId, " /app ")
        assertThat(storedSiteScanColumns()["site_scan_path"]).isEqualTo("/app")

        projectManagement.changeSiteScanPath(projectId, "")
        assertThat(storedSiteScanColumns()["site_scan_path"]).isNull()
    }

    @Test
    @DisplayName("틀린 시작 경로는 거부하고 저장하지 않는다")
    fun rejects_invalid_site_scan_path() {
        assertThatThrownBy { projectManagement.changeSiteScanPath(projectId, "//evil.example.com") }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_SITE_SCAN_PATH) }
        assertThat(storedSiteScanColumns()["site_scan_path"]).isNull()
    }

    @Test
    @DisplayName("사이트 점검 인증 정보를 저장하고 지운다")
    fun changes_and_clears_site_credentials() {
        projectManagement.changeSiteCredentials(projectId, "user", "pw")
        assertThat(storedSiteScanColumns()).containsEntry("site_auth_username", "user").containsEntry("site_auth_password", "pw")

        projectManagement.clearSiteCredentials(projectId)
        assertThat(storedSiteScanColumns()).containsEntry("site_auth_username", null).containsEntry("site_auth_password", null)
    }

    @Test
    @DisplayName("틀린 인증 정보는 거부하고 이전 값을 그대로 둔다")
    fun rejects_invalid_site_credentials() {
        projectManagement.changeSiteCredentials(projectId, "user", "pw")

        assertThatThrownBy { projectManagement.changeSiteCredentials(projectId, "user", "") }
            .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS) }
        assertThat(storedSiteScanColumns()).containsEntry("site_auth_username", "user").containsEntry("site_auth_password", "pw")
    }
}
