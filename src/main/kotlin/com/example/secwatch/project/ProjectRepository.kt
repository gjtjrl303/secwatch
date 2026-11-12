package com.example.secwatch.project

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProjectRepository : JpaRepository<Project, Long> {
    fun existsByDomain(domain: String): Boolean

    /** 프로젝트 하나의 의존성과 항목과 알림을 바꾸는 일을 한 줄로 세우는 행 잠금이다. 트랜잭션의 첫 쿼리로 잡아야 이후 조회가 커밋된 값을 본다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    fun findForUpdate(@Param("id") id: Long): Project?
}
