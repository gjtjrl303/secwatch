package com.example.secwatch.finding

import com.example.secwatch.common.Severity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SeverityCount {
    val projectId: Long
    val severity: Severity
    val total: Long
}

interface FindingRepository : JpaRepository<Finding, Long> {
    /** 목록 화면이 프로젝트마다 따로 세지 않도록 한 번에 센다. */
    @Query(
        """
        select f.project.id as projectId, f.severity as severity, count(f) as total
        from Finding f
        where f.status in :statuses
        group by f.project.id, f.severity
        """
    )
    fun countBySeverity(@Param("statuses") statuses: List<FindingStatus>): List<SeverityCount>

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

    /** 아직 알리지 않은 항목이다. 알림에 패키지 이름이 필요해서 의존성을 함께 읽는다. */
    @Query(
        """
        select f from Finding f join fetch f.dependency
        where f.project.id = :projectId and f.status = :status and f.notifiedAt is null
        """
    )
    fun findPendingNotification(@Param("projectId") projectId: Long, @Param("status") status: FindingStatus): List<Finding>

    /** 같은 항목의 상태 변경을 한 줄로 세운다. 의존성 행까지 잠그면 의존성을 지우는 가져오기와 잠금 순서가 엇갈려 교착이 날 수 있어서 항목 행만 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Finding f where f.id = :id")
    fun findForUpdate(@Param("id") id: Long): Finding?
}
