package com.example.secwatch.application

import com.example.secwatch.common.PackageReference
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import com.example.secwatch.support.osvVulnerability
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("점검 실행")
class CheckRunnerTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var checkRunner: CheckRunner
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository

    private var projectId = 0L

    @BeforeEach
    fun seed() {
        val project = Project("모꼬지", "m.example.com")
        project.changeWebhook("https://discord.com/api/webhooks/1/abc")
        projectId = projectRepository.save(project).id!!
        dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20"))
        fakeVulnerabilitySource.idsByPackageReference = mapOf(PackageReference("npm", "lodash", "4.17.20") to listOf("GHSA-1"))
        fakeVulnerabilitySource.vulnerabilities = mapOf("GHSA-1" to osvVulnerability("GHSA-1"))
    }

    private fun notifiedCount() =
        jdbcTemplate.queryForObject("select count(*) from finding where notified_at is not null", Int::class.java)!!

    @Test
    @DisplayName("점검한 뒤 새 항목을 알린다")
    fun checks_then_notifies() {
        checkRunner.run(projectId)

        assertThat(count("finding")).isEqualTo(1)
        assertThat(fakeNotifier.sent).hasSize(1)
        assertThat(notifiedCount()).isEqualTo(1)
    }

    @Test
    @DisplayName("점검이 실패해도 이전에 못 보낸 알림은 다시 보낸다")
    fun retries_unsent_notifications_even_when_check_fails() {
        fakeNotifier.fail = true
        checkRunner.run(projectId)
        assertThat(count("finding")).isEqualTo(1)
        assertThat(notifiedCount()).isEqualTo(0)

        fakeNotifier.fail = false
        fakeVulnerabilitySource.failIdLookup = true
        checkRunner.run(projectId)

        assertThat(fakeNotifier.sent).hasSize(1)
        assertThat(notifiedCount()).isEqualTo(1)
        val checkError = jdbcTemplate.queryForObject("select last_check_error from project where id = ?", String::class.java, projectId)
        assertThat(checkError).isNotNull()
    }
}
