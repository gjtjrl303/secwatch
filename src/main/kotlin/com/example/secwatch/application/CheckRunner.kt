package com.example.secwatch.application

import com.example.secwatch.finding.CheckService
import com.example.secwatch.notify.NewFindingNotifier
import org.springframework.stereotype.Component

@Component
class CheckRunner(
    private val checkService: CheckService,
    private val newFindingNotifier: NewFindingNotifier,
) {
    /** 점검이 실패해도 알림은 시도한다. 이전에 못 보낸 항목을 다시 보내기 위해서다. */
    fun run(projectId: Long) {
        checkService.check(projectId)
        newFindingNotifier.notifyPending(projectId)
    }
}
