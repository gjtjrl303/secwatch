package com.example.secwatch.application

import com.example.secwatch.project.ProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DailyRecheck(
    private val projectRepository: ProjectRepository,
    private val checkRunner: CheckRunner,
) {
    private val log = LoggerFactory.getLogger(DailyRecheck::class.java)

    /** 시각은 secwatch.recheck.cron으로 바꾸고, "-"로 두면 끈다. 한 프로젝트가 실패해도 나머지를 계속 점검한다. */
    @Scheduled(cron = "\${secwatch.recheck.cron}", zone = "Asia/Seoul")
    fun run() {
        for (projectId in projectRepository.findAllIds()) {
            try {
                checkRunner.run(projectId)
            } catch (e: Exception) {
                log.error("재점검에 실패했습니다. projectId={}", projectId, e)
            }
        }
    }
}
