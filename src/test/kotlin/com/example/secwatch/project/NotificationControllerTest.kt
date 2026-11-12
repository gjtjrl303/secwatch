package com.example.secwatch.project

import com.example.secwatch.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@DisplayName("알림 설정 API")
class NotificationControllerTest : MySqlIntegrationTest() {
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var mockMvc: MockMvc

    private var projectId = 0L

    @BeforeEach
    fun seed() {
        jdbcTemplate.update("delete from project")
        jdbcTemplate.update("insert into project(name, domain) values ('t', 'n.example.com')")
        projectId = jdbcTemplate.queryForObject("select id from project", Long::class.java)!!
    }

    private fun put(id: Long, url: String) = mockMvc.perform(
        put("/api/projects/$id/notification")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"webhookUrl":"$url"}""")
    )

    private fun storedWebhook() =
        jdbcTemplate.queryForObject("select webhook_url from project where id = ?", String::class.java, projectId)

    @Test
    @DisplayName("디스코드 주소가 아니면 400과 안내 문구를 돌려준다")
    fun rejects_non_discord_url() {
        put(projectId, "https://evil.example.com/api/webhooks/1/a")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40002))
            .andExpect(jsonPath("$.message").value("디스코드 웹훅 주소를 확인해주세요."))
        assertThat(storedWebhook()).isNull()
    }

    @Test
    @DisplayName("http 주소나 뒤가 빈 주소, 빈 값은 거부한다")
    fun rejects_http_and_empty_tail() {
        put(projectId, "http://discord.com/api/webhooks/1/a").andExpect(status().isBadRequest)
        put(projectId, "https://discord.com/api/webhooks/").andExpect(status().isBadRequest)
        put(projectId, "").andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("discord.com과 discordapp.com 주소는 받아들여 저장한다")
    fun accepts_discord_com_and_discordapp_com() {
        put(projectId, "https://discord.com/api/webhooks/1/a").andExpect(status().isNoContent)
        assertThat(storedWebhook()).isEqualTo("https://discord.com/api/webhooks/1/a")

        put(projectId, "https://discordapp.com/api/webhooks/2/b").andExpect(status().isNoContent)
        assertThat(storedWebhook()).isEqualTo("https://discordapp.com/api/webhooks/2/b")
    }

    @Test
    @DisplayName("응답에 웹훅 주소를 절대 싣지 않는다")
    fun response_never_contains_webhook_url() {
        val result = put(projectId, "https://discord.com/api/webhooks/1/topsecret").andExpect(status().isNoContent).andReturn()
        assertThat(result.response.contentAsString).isEmpty()

        val body = mockMvc.perform(get("/api/projects/$projectId")).andReturn().response.contentAsString
        assertThat(body).doesNotContain("topsecret").doesNotContain("webhooks")
    }

    @Test
    @DisplayName("없는 프로젝트에 설정하면 404와 안내 문구를 돌려준다")
    fun unknown_project_is_404() {
        put(999999, "https://discord.com/api/webhooks/1/a")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("웹훅 주소가 500자를 넘으면 400을 돌려주고 500자는 받아들인다")
    fun webhook_length_limit() {
        val prefix = "https://discord.com/api/webhooks/"
        put(projectId, prefix + "a".repeat(468))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40002))
        put(projectId, prefix + "a".repeat(467)).andExpect(status().isNoContent)
    }
}
