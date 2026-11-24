package com.example.secwatch.project

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ProjectTest {

    @Test
    @DisplayName("이름과 도메인을 함께 바꾼다")
    fun update_changes_name_and_domain() {
        val project = Project("모꼬지", "mokkoji.kr")

        project.update("새 이름", "new.example.com")

        assertThat(project.name).isEqualTo("새 이름")
        assertThat(project.domain).isEqualTo("new.example.com")
    }

    @Test
    @DisplayName("사이트 점검 인증 정보를 저장하고 지운다")
    fun site_credentials_can_be_set_and_cleared() {
        val project = Project("모꼬지", "mokkoji.kr")
        assertThat(project.siteCredentials).isNull()

        project.changeSiteCredentials(SiteCredentials("user", "pw"))
        assertThat(project.siteCredentials?.username).isEqualTo("user")
        assertThat(project.siteCredentials?.password).isEqualTo("pw")

        project.clearSiteCredentials()
        assertThat(project.siteCredentials).isNull()
    }

    @Test
    @DisplayName("웹훅 주소를 바꾼다")
    fun changeWebhook_changes_webhook_url() {
        val project = Project("모꼬지", "mokkoji.kr")

        project.changeWebhook("https://discord.com/api/webhooks/1/a")

        assertThat(project.webhookUrl).isEqualTo("https://discord.com/api/webhooks/1/a")
    }

    @Test
    @DisplayName("점검에 성공하면 점검 시각을 기록하고 이전 오류를 지운다")
    fun markChecked_records_time_and_clears_error() {
        val project = Project("모꼬지", "mokkoji.kr")
        project.markCheckFailed("실패")
        val checkedAt = LocalDateTime.of(2026, 11, 16, 9, 0)

        project.markChecked(checkedAt)

        assertThat(project.lastCheckedAt).isEqualTo(checkedAt)
        assertThat(project.lastCheckError).isNull()
    }

    @Test
    @DisplayName("점검에 실패하면 오류를 기록하고 점검 시각은 그대로 둔다")
    fun markCheckFailed_records_error_and_keeps_checked_time() {
        val project = Project("모꼬지", "mokkoji.kr")
        val checkedAt = LocalDateTime.of(2026, 11, 16, 9, 0)
        project.markChecked(checkedAt)

        project.markCheckFailed("실패")

        assertThat(project.lastCheckError).isEqualTo("실패")
        assertThat(project.lastCheckedAt).isEqualTo(checkedAt)
    }

    @Test
    @DisplayName("알림에 성공하면 이전 알림 오류를 지운다")
    fun markNotifySucceeded_clears_error() {
        val project = Project("모꼬지", "mokkoji.kr")
        project.markNotifyFailed("실패")

        project.markNotifySucceeded()

        assertThat(project.lastNotifyError).isNull()
    }

    @Test
    @DisplayName("알림에 실패하면 오류를 기록한다")
    fun markNotifyFailed_records_error() {
        val project = Project("모꼬지", "mokkoji.kr")

        project.markNotifyFailed("실패")

        assertThat(project.lastNotifyError).isEqualTo("실패")
    }
}
