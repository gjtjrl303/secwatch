package com.example.secwatch.project

import com.example.secwatch.common.SecretCipher
import com.example.secwatch.support.MySqlIntegrationTest
import java.util.Base64
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate

@DisplayName("시작할 때 저장된 비밀 값을 지금 키로 풀 수 있는지 확인")
class StoredSecretsCheckTest : MySqlIntegrationTest() {
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var storedSecretsCheck: StoredSecretsCheck

    private val otherCipher = SecretCipher(Base64.getEncoder().encodeToString(ByteArray(32) { 9 }))

    @BeforeEach
    fun clear() {
        jdbcTemplate.update("delete from project")
    }

    private var count = 0

    private fun insert(webhook: String?, password: String?) {
        count += 1
        jdbcTemplate.update(
            "insert into project(name, domain, webhook_url, site_auth_username, site_auth_password) values (?, ?, ?, ?, ?)",
            "p$count", "p$count.example.com", webhook, password?.let { "user" }, password,
        )
    }

    private fun run() = storedSecretsCheck.run(DefaultApplicationArguments())

    @Test
    @DisplayName("지금 키로 저장한 값과 빈 값은 통과한다")
    fun passes_with_the_same_key() {
        insert(secretCipher.encrypt("https://discord.com/api/webhooks/1/a"), secretCipher.encrypt("pw"))
        insert(null, null)

        assertThatCode { run() }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("다른 키로 저장한 웹훅 주소가 있으면 앱을 시작하지 않는다")
    fun fails_when_webhook_was_saved_with_another_key() {
        insert(otherCipher.encrypt("https://discord.com/api/webhooks/1/a"), null)

        assertThatThrownBy { run() }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("암호화 키")
    }

    @Test
    @DisplayName("다른 키로 저장한 비밀번호가 있으면 앱을 시작하지 않는다")
    fun fails_when_password_was_saved_with_another_key() {
        insert(null, otherCipher.encrypt("pw"))

        assertThatThrownBy { run() }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("암호화 키")
    }
}
