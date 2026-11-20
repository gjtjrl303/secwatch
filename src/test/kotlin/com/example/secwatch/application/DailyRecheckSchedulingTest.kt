package com.example.secwatch.application

import com.example.secwatch.common.PackageReference
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import com.example.secwatch.support.osvVulnerability
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** 다른 테스트는 스케줄러를 꺼 두므로, 여기서는 매초 실행되게 켜서 스케줄러 연결 자체를 확인한다. */
@DisplayName("매일 재점검의 스케줄러 연결")
@SpringBootTest(properties = ["secwatch.recheck.cron=*/1 * * * * *"])
class DailyRecheckSchedulingTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository

    @Test
    @DisplayName("정해진 시각이 되면 아무도 누르지 않아도 재점검이 저절로 실행된다")
    fun scheduler_runs_the_recheck_by_itself() {
        val project = projectRepository.save(Project("자동", "auto.example.com"))
        dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20"))
        fakeVulnerabilitySource.idsByPackageReference = mapOf(PackageReference("npm", "lodash", "4.17.20") to listOf("GHSA-1"))
        fakeVulnerabilitySource.vulnerabilities = mapOf("GHSA-1" to osvVulnerability("GHSA-1"))

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertThat(count("finding")).isEqualTo(1)
        }
    }
}
