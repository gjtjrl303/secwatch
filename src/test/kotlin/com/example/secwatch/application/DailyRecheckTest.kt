package com.example.secwatch.application

import com.example.secwatch.common.PackageReference
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import com.example.secwatch.support.osvVulnerability
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("매일 재점검")
class DailyRecheckTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var dailyRecheck: DailyRecheck
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository

    private fun saveProject(name: String, domain: String): Project {
        val project = Project(name, domain)
        project.changeWebhook("https://discord.com/api/webhooks/1/$name")
        val saved = projectRepository.save(project)
        dependencyRepository.save(Dependency(saved, "npm", "lodash", "4.17.20"))
        return saved
    }

    private fun checkedCount() =
        jdbcTemplate.queryForObject("select count(*) from project where last_checked_at is not null", Int::class.java)!!

    @Test
    @DisplayName("모든 프로젝트를 점검하고 알린다")
    fun rechecks_every_project() {
        saveProject("a", "a.example.com")
        saveProject("b", "b.example.com")
        fakeVulnerabilitySource.idsByPackageReference = mapOf(PackageReference("npm", "lodash", "4.17.20") to listOf("GHSA-1"))
        fakeVulnerabilitySource.vulnerabilities = mapOf("GHSA-1" to osvVulnerability("GHSA-1"))

        dailyRecheck.run()

        assertThat(checkedCount()).isEqualTo(2)
        assertThat(count("finding")).isEqualTo(2)
        assertThat(fakeNotifier.sent).hasSize(2)
    }

    @Test
    @DisplayName("한 프로젝트가 예기치 않게 실패해도 나머지를 계속 처리한다")
    fun one_failing_project_does_not_stop_the_others() {
        saveProject("a", "a.example.com")
        saveProject("b", "b.example.com")
        fakeVulnerabilitySource.idsByPackageReference = mapOf(PackageReference("npm", "lodash", "4.17.20") to listOf("GHSA-1"))
        fakeVulnerabilitySource.vulnerabilities = mapOf("GHSA-1" to osvVulnerability("GHSA-1"))
        val calls = AtomicInteger()
        fakeNotifier.onSend = { if (calls.incrementAndGet() == 1) throw IllegalStateException("boom") }

        dailyRecheck.run()

        assertThat(checkedCount()).isEqualTo(2)
        assertThat(fakeNotifier.sent).hasSize(1)
    }
}
