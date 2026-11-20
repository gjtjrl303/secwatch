package com.example.secwatch.application

import com.example.secwatch.finding.CheckService
import com.example.secwatch.notify.NewFindingNotifier
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@DisplayName("점검 실행의 예외 처리")
class CheckRunnerFailureTest {
    private val checkService = mock(CheckService::class.java)
    private val newFindingNotifier = mock(NewFindingNotifier::class.java)
    private val checkRunner = CheckRunner(checkService, newFindingNotifier)

    @Test
    @DisplayName("점검이 예외를 던져도 못 보낸 알림은 시도한다")
    fun notifies_even_when_the_check_throws() {
        willThrow(IllegalStateException("db down")).given(checkService).check(1L)

        checkRunner.run(1L)

        verify(newFindingNotifier).notifyPending(1L)
    }
}
