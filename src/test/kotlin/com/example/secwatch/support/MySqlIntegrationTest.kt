package com.example.secwatch.support

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

@SpringBootTest(properties = ["secwatch.recheck.cron=-"])
@Import(MySqlContainerConfig::class)
abstract class MySqlIntegrationTest
