package com.example.secwatch.application

import com.example.secwatch.finding.CheckService
import com.example.secwatch.notify.NewFindingNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CheckRunner(
    private val checkService: CheckService,
    private val newFindingNotifier: NewFindingNotifier,
) {
    private val log = LoggerFactory.getLogger(CheckRunner::class.java)

    /** 점검이 실패하거나 예외를 던져도 알림은 시도한다. 이전에 못 보낸 항목을 다시 보내기 위해서다. */
    fun run(projectId: Long) {
        try {
            checkService.check(projectId)
        } catch (e: Exception) {
            log.error("점검 중 예외가 났습니다. 알림은 계속 시도합니다. projectId={}", projectId, e)
        }
        newFindingNotifier.notifyPending(projectId)
    }
}
