package com.example.secwatch.finding

import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.osv.VulnerabilitySource
import com.example.secwatch.project.ProjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus

@DisplayName("점검 서비스의 실패 처리")
class CheckServiceFailureTest {

    @Test
    @DisplayName("실패를 기록하다가 또 실패해도 예외를 호출자에게 던지지 않는다")
    fun failure_while_recording_failure_does_not_propagate() {
        val projectRepository = mock(ProjectRepository::class.java)
        `when`(projectRepository.existsById(1L)).thenReturn(true)
        val dependencyRepository = mock(DependencyRepository::class.java)
        `when`(dependencyRepository.findByProjectId(1L)).thenThrow(RuntimeException("db down"))
        val brokenTransactionManager = object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
                throw IllegalStateException("db down")

            override fun commit(status: TransactionStatus) {}

            override fun rollback(status: TransactionStatus) {}
        }
        val checkService = CheckService(
            projectRepository,
            dependencyRepository,
            mock(VulnerabilitySource::class.java),
            mock(FindingReconciler::class.java),
            brokenTransactionManager,
        )

        val result = checkService.check(1L)

        assertThat(result.newFindingIds).isEmpty()
    }
}
