package com.example.secwatch.project

import com.example.secwatch.support.MySqlIntegrationTest
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@DisplayName("이미 평문으로 저장된 비밀 값 암호화")
class EncryptExistingSecretsTest : MySqlIntegrationTest() {
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Autowired lateinit var dataSource: DataSource

    @Autowired lateinit var migration: V9__encrypt_existing_secrets

    @Autowired lateinit var projectManagement: ProjectManagement

    @Test
    @DisplayName("평문 웹훅 주소와 비밀번호를 암호화하고, 비어 있는 값은 그대로 둔다")
    fun encrypts_plain_values_and_keeps_nulls() {
        jdbcTemplate.update("delete from project")
        jdbcTemplate.update(
            "insert into project(name, domain, webhook_url, site_auth_username, site_auth_password) values " +
                "('a', 'a.example.com', 'https://discord.com/api/webhooks/1/old-token', 'user', 'old-pw'), " +
                "('b', 'b.example.com', null, null, null)",
        )

        dataSource.connection.use(migration::encryptExisting)

        val rows = jdbcTemplate.queryForList("select name, webhook_url, site_auth_username, site_auth_password from project order by name")
        assertThat(rows[0]["webhook_url"] as String).startsWith("v1:").doesNotContain("old-token")
        assertThat(rows[0]["site_auth_password"] as String).startsWith("v1:").doesNotContain("old-pw")
        assertThat(rows[0]["site_auth_username"]).isEqualTo("user")
        assertThat(rows[1]["webhook_url"]).isNull()
        assertThat(rows[1]["site_auth_password"]).isNull()

        val project = projectManagement.list().first { it.name == "a" }
        assertThat(project.webhookUrl).isEqualTo("https://discord.com/api/webhooks/1/old-token")
        assertThat(project.siteCredentials?.password).isEqualTo("old-pw")
    }

    @Test
    @DisplayName("Flyway가 이 작업을 9번 마이그레이션으로 실행했다")
    fun flyway_ran_the_migration() {
        val applied = jdbcTemplate.queryForList("select version, success from flyway_schema_history where version = '9'")

        assertThat(applied).hasSize(1)
        assertThat(applied[0]["success"]).isIn(true, 1, 1L, 1.toByte())
    }
}
