package com.example.secwatch.application

import com.example.secwatch.dependency.DependenciesImported
import java.util.concurrent.Executor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CheckTrigger(
    private val checkRunner: CheckRunner,
    @Qualifier("checkExecutor") private val checkExecutor: Executor,
) {
    private val log = LoggerFactory.getLogger(CheckTrigger::class.java)

    /** 커밋된 뒤에만 반응해서 새 의존성이 보이는 상태로 점검한다. 대기열이 가득 차면 버리고, 매일 재점검이 따라잡는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDependenciesImported(event: DependenciesImported) {
        try {
            checkExecutor.execute { runSafely(event.projectId) }
        } catch (e: TaskRejectedException) {
            log.warn("점검 요청이 가득 차 건너뜁니다. 다음 재점검 때 확인합니다. projectId={}", event.projectId)
        }
    }

    private fun runSafely(projectId: Long) {
        try {
            checkRunner.run(projectId)
        } catch (e: Exception) {
            log.error("가져온 직후 점검에 실패했습니다. projectId={}", projectId, e)
        }
    }
}
