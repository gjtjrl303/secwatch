package com.example.secwatch.support

import com.example.secwatch.common.SecretCipher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.containers.MySQLContainer

@TestConfiguration(proxyBeanMethods = false)
class MySqlContainerConfig {
    @Bean
    @ServiceConnection
    fun mysql(): MySQLContainer<*> = MySQLContainer("mysql:8.0")
}

@SpringBootTest(properties = ["secwatch.recheck.cron=-", "secwatch.site-scan.allowed-hosts=localhost"])
@Import(MySqlContainerConfig::class)
abstract class MySqlIntegrationTest {
    @Autowired protected lateinit var secretCipher: SecretCipher

    /** DB에 암호화해 저장한 값을 풀어서 돌려준다. 비어 있으면 null이다. */
    protected fun decrypted(stored: Any?): String? = (stored as String?)?.let(secretCipher::decrypt)
}
