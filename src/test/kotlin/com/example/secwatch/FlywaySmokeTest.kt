package com.example.secwatch

import com.example.secwatch.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@DisplayName("DB 마이그레이션")
class FlywaySmokeTest : MySqlIntegrationTest() {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    @DisplayName("마이그레이션이 프로젝트와 의존성 테이블을 만든다")
    fun flyway_creates_core_tables() {
        val tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = database()",
            String::class.java
        ).map { it.lowercase() }

        assertThat(tables).contains("project", "dependency")
    }
}
