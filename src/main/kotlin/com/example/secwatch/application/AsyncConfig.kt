package com.example.secwatch.application

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableScheduling
class AsyncConfig {
    /** 서버 메모리가 작아서 스레드 풀을 작게 잡았다. 점검은 대부분 OSV를 기다리는 시간이다. */
    @Bean("checkExecutor")
    fun checkExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = CHECK_THREADS
        maxPoolSize = CHECK_THREADS
        queueCapacity = CHECK_QUEUE_CAPACITY
        setThreadNamePrefix("check-")
    }

    /** ZAP이 메모리를 1GB 가까이 써서 한 번에 하나만 돌린다. 기다리는 요청은 받지 않는다. */
    @Bean("siteScanExecutor")
    fun siteScanExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 1
        queueCapacity = 0
        setThreadNamePrefix("site-scan-")
    }

    private companion object {
        const val CHECK_THREADS = 2
        const val CHECK_QUEUE_CAPACITY = 20
    }
}
