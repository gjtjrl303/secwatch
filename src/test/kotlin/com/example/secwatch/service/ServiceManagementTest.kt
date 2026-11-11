package com.example.secwatch.service

import com.example.secwatch.common.InvalidInputException
import com.example.secwatch.common.NotFoundException
import com.example.secwatch.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@DisplayName("서비스 조회와 알림 설정")
class ServiceManagementTest : MySqlIntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var management: ServiceManagement

    private var serviceId = 0L

    @BeforeEach
    fun seed() {
        jdbc.update("delete from monitored_service")
        jdbc.update("insert into monitored_service(name, domain) values ('t', 'm.example.com')")
        serviceId = jdbc.queryForObject("select id from monitored_service", Long::class.java)!!
    }

    private fun storedWebhook() =
        jdbc.queryForObject("select webhook_url from monitored_service where id = ?", String::class.java, serviceId)

    @Test
    @DisplayName("등록된 서비스를 모두 돌려준다")
    fun lists_all_services() {
        assertThat(management.list()).extracting<String> { it.domain }.containsExactly("m.example.com")
    }

    @Test
    @DisplayName("id로 서비스 하나를 찾는다")
    fun gets_service_by_id() {
        assertThat(management.get(serviceId).domain).isEqualTo("m.example.com")
    }

    @Test
    @DisplayName("없는 id로 찾으면 NotFoundException이 난다")
    fun get_missing_service_fails() {
        assertThatThrownBy { management.get(serviceId + 999) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("서비스를 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("올바른 디스코드 주소는 저장한다")
    fun changes_webhook() {
        management.changeWebhook(serviceId, "https://discord.com/api/webhooks/1/a")
        assertThat(storedWebhook()).isEqualTo("https://discord.com/api/webhooks/1/a")
    }

    @Test
    @DisplayName("디스코드 주소가 아니면 거부하고 저장하지 않는다")
    fun rejects_invalid_webhook() {
        assertThatThrownBy { management.changeWebhook(serviceId, "https://evil.example.com/api/webhooks/1/a") }
            .isInstanceOf(InvalidInputException::class.java)
        assertThat(storedWebhook()).isNull()
    }

    @Test
    @DisplayName("없는 서비스의 알림을 바꾸면 NotFoundException이 난다")
    fun change_webhook_of_missing_service_fails() {
        assertThatThrownBy { management.changeWebhook(serviceId + 999, "https://discord.com/api/webhooks/1/a") }
            .isInstanceOf(NotFoundException::class.java)
    }
}
