package com.example.secwatch.project

import com.example.secwatch.common.SecretCipher
import java.sql.Connection
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.stereotype.Component

internal val PROJECT_SECRET_COLUMNS = listOf("webhook_url", "site_auth_password")

/** 암호화를 넣기 전에 평문으로 저장한 웹훅 주소와 사이트 점검 비밀번호를 한 번 암호화한다. */
@Component
@Suppress("ClassName")
class V9__encrypt_existing_secrets(private val secretCipher: SecretCipher) : BaseJavaMigration() {

    override fun migrate(context: Context) = encryptExisting(context.connection)

    fun encryptExisting(connection: Connection) {
        PROJECT_SECRET_COLUMNS.forEach { column ->
            val plainById = connection.prepareStatement("select id, $column from project where $column is not null").use { select ->
                select.executeQuery().use { rows ->
                    generateSequence { if (rows.next()) rows.getLong("id") to rows.getString(column) else null }.toList()
                }
            }
            connection.prepareStatement("update project set $column = ? where id = ?").use { update ->
                plainById.forEach { (id, plain) ->
                    update.setString(1, secretCipher.encrypt(plain))
                    update.setLong(2, id)
                    update.executeUpdate()
                }
            }
        }
    }
}
