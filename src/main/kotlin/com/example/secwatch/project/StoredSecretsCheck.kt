package com.example.secwatch.project

import com.example.secwatch.common.SecretCipher
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** 키가 저장할 때와 다르면 화면마다 오류가 나므로, 시작할 때 한 번 풀어 보고 맞지 않으면 앱을 멈춘다. */
@Component
class StoredSecretsCheck(private val jdbcTemplate: JdbcTemplate, private val secretCipher: SecretCipher) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        PROJECT_SECRET_COLUMNS.forEach { column ->
            jdbcTemplate.queryForList("select $column from project where $column is not null", String::class.java)
                .forEach(secretCipher::decrypt)
        }
    }
}
