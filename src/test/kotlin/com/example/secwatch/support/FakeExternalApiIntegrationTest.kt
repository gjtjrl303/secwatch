package com.example.secwatch.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(MySqlContainerConfig::class, FakeSourceConfig::class)
abstract class FakeExternalApiIntegrationTest : DbIntegrationTest() {
    @Autowired
    lateinit var fakeVulnerabilitySource: FakeVulnerabilitySource

    @Autowired
    lateinit var fakeNotifier: FakeNotifier

    @Autowired
    lateinit var fakeSiteScanner: FakeSiteScanner

    @BeforeEach
    fun resetFakes() {
        fakeVulnerabilitySource.reset()
        fakeNotifier.reset()
        fakeSiteScanner.reset()
    }
}
