package com.example.secwatch.sitescan

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("사이트 점검 기록의 동시 요청")
class SiteScanRecorderConcurrencyTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var recorder: SiteScanRecorder

    private var projectId = 0L
    private var otherProjectId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("insert into project(name, domain) values ('a', 'a.example.com'), ('b', 'b.example.com')")
        projectId = jdbcTemplate.queryForObject("select id from project where name = 'a'", Long::class.java)!!
        otherProjectId = jdbcTemplate.queryForObject("select id from project where name = 'b'", Long::class.java)!!
    }

    /** 두 작업을 같은 순간에 시작해서, 각각 정상 결과("ok")나 안내용 오류 코드나 그 밖의 예외 이름을 돌려준다. */
    private fun together(first: () -> Unit, second: () -> Unit): List<String> {
        val pool = Executors.newFixedThreadPool(2)
        val barrier = CyclicBarrier(2)
        try {
            val tasks = listOf(first, second).map { action ->
                Callable {
                    barrier.await(10, TimeUnit.SECONDS)
                    try {
                        action()
                        "ok"
                    } catch (e: SecwatchException) {
                        e.errorCode.name
                    } catch (e: Exception) {
                        e.javaClass.simpleName
                    }
                }
            }
            return pool.invokeAll(tasks, 30, TimeUnit.SECONDS).map { future -> future.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun clearScans() {
        jdbcTemplate.update("delete from site_finding")
        jdbcTemplate.update("delete from site_scan")
    }

    @Test
    @DisplayName("진행 중인 점검이 없을 때 서로 다른 프로젝트가 동시에 시작하면 하나만 시작되고 나머지는 진행 중 안내를 받는다")
    fun concurrent_starts_let_only_one_through() {
        repeat(ROUNDS) {
            clearScans()

            val results = together({ recorder.start(projectId) }, { recorder.start(otherProjectId) })

            assertThat(results).containsExactlyInAnyOrder("ok", ErrorCode.SCAN_IN_PROGRESS.name)
            assertThat(jdbcTemplate.queryForObject("select count(*) from site_scan where status = 'RUNNING'", Int::class.java)).isEqualTo(1)
        }
    }

    @Test
    @DisplayName("결과 저장과 같은 프로젝트의 새 점검 요청이 겹쳐도 결과는 저장되고 요청은 안내용 오류만 받는다")
    fun succeed_and_start_on_the_same_project_do_not_deadlock() {
        val alert = SiteAlert("10038-1", "name", SiteRisk.MEDIUM, "description", "solution", "reference", 693, listOf("http://a.example.com/"))
        repeat(ROUNDS) {
            clearScans()
            val scan = recorder.start(projectId)

            val results = together({ recorder.succeed(scan.id!!, "{}", listOf(alert)) }, { recorder.start(projectId) })

            assertThat(results[0]).isEqualTo("ok")
            assertThat(results[1]).isIn(ErrorCode.SCAN_IN_PROGRESS.name, ErrorCode.SCAN_TOO_SOON.name)
            assertThat(jdbcTemplate.queryForObject("select status from site_scan where id = ?", String::class.java, scan.id)).isEqualTo("SUCCEEDED")
            assertThat(jdbcTemplate.queryForObject("select count(*) from site_finding", Int::class.java)).isEqualTo(1)
        }
    }

    private companion object {
        const val ROUNDS = 20
    }
}
