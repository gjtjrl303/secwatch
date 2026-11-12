package com.example.secwatch.service

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MonitoredServiceRepository : JpaRepository<MonitoredService, Long> {
    fun existsByDomain(domain: String): Boolean

    /** 서비스 하나의 의존성과 항목과 알림을 바꾸는 일을 한 줄로 세우는 행 잠금이다. 트랜잭션의 첫 쿼리로 잡아야 이후 조회가 커밋된 값을 본다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonitoredService s where s.id = :id")
    fun findForUpdate(@Param("id") id: Long): MonitoredService?
}
