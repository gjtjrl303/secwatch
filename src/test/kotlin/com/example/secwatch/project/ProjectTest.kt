package com.example.secwatch.project

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
    @DisplayName("웹훅 주소를 바꾼다")
    fun changeWebhook_changes_webhook_url() {
        val project = Project("모꼬지", "mokkoji.kr")

        project.changeWebhook("https://discord.com/api/webhooks/1/a")

        assertThat(project.webhookUrl).isEqualTo("https://discord.com/api/webhooks/1/a")
    }
}
