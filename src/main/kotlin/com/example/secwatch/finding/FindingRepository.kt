package com.example.secwatch.finding

import com.example.secwatch.common.Severity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FindingRepository : JpaRepository<Finding, Long> {
    /** 의존성을 함께 읽어서 항목마다 따로 조회하지 않는다. 거를 값이 null이면 그 조건은 쓰지 않는다. */
    @Query(
        """
        select f from Finding f join fetch f.dependency
        where f.project.id = :projectId
          and (:status is null or f.status = :status)
          and (:severity is null or f.severity = :severity)
        """
    )
    fun search(
        @Param("projectId") projectId: Long,
        @Param("status") status: FindingStatus?,
        @Param("severity") severity: Severity?,
    ): List<Finding>

    @Query("select f from Finding f join fetch f.dependency where f.id = :id")
    fun findWithDependency(@Param("id") id: Long): Finding?
}
