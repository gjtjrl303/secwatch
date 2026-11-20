package com.example.secwatch.application

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.secwatch.common.PackageReference
import com.example.secwatch.dependency.DependenciesImported
import com.example.secwatch.dependency.DependencyImporter
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import com.example.secwatch.support.osvVulnerability
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("가져온 직후 자동 점검")
class CheckTriggerTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var dependencyImporter: DependencyImporter
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var applicationEventPublisher: ApplicationEventPublisher

    private var projectId = 0L
    private val lock = """{"lockfileVersion":3,"packages":{"":{},"node_modules/lodash":{"version":"4.17.20"}}}"""
    private val logAppender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(CheckTrigger::class.java) as Logger

    @BeforeEach
    fun seed() {
        val project = Project("모꼬지", "m.example.com")
        project.changeWebhook("https://discord.com/api/webhooks/1/abc")
        projectId = projectRepository.save(project).id!!
        fakeVulnerabilitySource.idsByPackageReference = mapOf(PackageReference("npm", "lodash", "4.17.20") to listOf("GHSA-1"))
        fakeVulnerabilitySource.vulnerabilities = mapOf("GHSA-1" to osvVulnerability("GHSA-1"))
        logAppender.start()
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    @Test
    @DisplayName("가져오면 커밋된 뒤에 점검하고 알림까지 이어진다")
    fun import_triggers_check_then_notify() {
        dependencyImporter.import(projectId, lock)

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(count("finding")).isEqualTo(1)
            assertThat(fakeNotifier.sent).hasSize(1)
        }
    }

    @Test
    @DisplayName("가져오기는 점검이 끝나기를 기다리지 않고 바로 끝난다")
    fun import_does_not_wait_for_the_check() {
        val sending = CountDownLatch(1)
        val release = CountDownLatch(1)
        fakeNotifier.onSend = {
            sending.countDown()
            release.await(30, TimeUnit.SECONDS)
        }
        try {
            dependencyImporter.import(projectId, lock)

            assertThat(sending.await(15, TimeUnit.SECONDS)).isTrue()
            assertThat(fakeNotifier.sent).isEmpty()
        } finally {
            release.countDown()
        }
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(fakeNotifier.sent).hasSize(1)
        }
    }

    @Test
    @DisplayName("롤백된 트랜잭션의 이벤트에는 반응하지 않는다")
    fun listener_ignores_rolled_back_transactions() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            applicationEventPublisher.publishEvent(DependenciesImported(projectId))
            status.setRollbackOnly()
        }

        Awaitility.await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(3)).untilAsserted {
            assertThat(count("finding")).isEqualTo(0)
            assertThat(fakeNotifier.sent).isEmpty()
            assertThat(projectRepository.findById(projectId).orElseThrow().lastCheckedAt).isNull()
        }
    }

    @Test
    @DisplayName("점검 대기열이 가득 차면 버리고 경고만 남기며 가져오기는 성공한다")
    fun full_queue_drops_the_check_but_import_succeeds() {
        val release = CountDownLatch(1)
        try {
            // 스레드 2개가 실제로 붙잡힌 것을 확인한 뒤 대기열 20칸을 채운다
            val started = CountDownLatch(2)
            repeat(2) {
                checkExecutor.execute {
                    started.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }
            }
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
            repeat(20) { checkExecutor.execute { release.await(30, TimeUnit.SECONDS) } }

            val result = dependencyImporter.import(projectId, lock)

            assertThat(result.parsed).isEqualTo(1)
            assertThat(count("dependency")).isEqualTo(1)
            val logged = logAppender.list.joinToString("\n") { event -> event.formattedMessage }
            assertThat(logged).contains("점검 요청이 가득 차 건너뜁니다").contains("projectId=$projectId")
        } finally {
            release.countDown()
        }
    }
}
