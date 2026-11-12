package com.example.secwatch.support

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

abstract class DbIntegrationTest : MySqlIntegrationTest() {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.update("delete from project")
    }

    /** 다른 트랜잭션이 프로젝트 행을 잠그고 [uncommitted]를 커밋 없이 실행하는 동안 [action]을 두 번째 스레드에서 시작한다. [action]은 기다렸다가 첫 트랜잭션이 커밋된 뒤에 이어진다. */
    fun <T> blockedWhileProjectIsLocked(projectId: Long, uncommitted: () -> Unit = {}, action: () -> T): T {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        Thread {
            TransactionTemplate(transactionManager).executeWithoutResult {
                jdbcTemplate.queryForObject("select id from project where id = ? for update", Long::class.java, projectId)
                uncommitted()
                locked.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }.start()
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue()

        val future = CompletableFuture.supplyAsync(action)
        try {
            assertThatThrownBy { future.get(1, TimeUnit.SECONDS) }.isInstanceOf(TimeoutException::class.java)
        } finally {
            release.countDown()
        }
        return future.get(15, TimeUnit.SECONDS)
    }

    fun count(table: String): Int = jdbcTemplate.queryForObject("select count(*) from $table", Int::class.java)!!
}
