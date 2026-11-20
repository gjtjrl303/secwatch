package com.example.secwatch.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(MySqlContainerConfig::class, FakeSourceConfig::class)
abstract class FakeOsvIntegrationTest : DbIntegrationTest() {
    @Autowired
    lateinit var fakeVulnerabilitySource: FakeVulnerabilitySource

    @BeforeEach
    fun resetFakes() {
        fakeVulnerabilitySource.reset()
    }
}
