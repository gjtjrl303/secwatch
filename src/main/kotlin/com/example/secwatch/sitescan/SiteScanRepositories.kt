package com.example.secwatch.sitescan

import com.example.secwatch.project.Project
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** 목록에 필요한 값만 읽는다. 원본 결과(raw_json)는 커서 목록에서는 읽지 않는다. */
interface SiteScanSummary {
    val id: Long
    val scanType: SiteScanType
    val status: SiteScanStatus
    val startedAt: LocalDateTime
    val finishedAt: LocalDateTime?
    val errorMessage: String?
}

interface SiteScanRepository : JpaRepository<SiteScan, Long> {
    @Query(value = "select id from site_scan_lock where id = 1 for update", nativeQuery = true)
    fun lockAllStarts(): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = (select s.project.id from SiteScan s where s.id = :scanId)")
    fun lockProjectOf(@Param("scanId") scanId: Long): Project?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SiteScan s where s.status = :status")
    fun findByStatusForUpdate(@Param("status") status: SiteScanStatus): List<SiteScan>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SiteScan s where s.id = :id and s.status = :status")
    fun findForUpdate(@Param("id") id: Long, @Param("status") status: SiteScanStatus): SiteScan?

    fun findFirstByProjectIdAndStatusOrderByStartedAtDesc(projectId: Long, status: SiteScanStatus): SiteScanSummary?

    fun findTop20ByProjectIdOrderByStartedAtDesc(projectId: Long): List<SiteScanSummary>
}

interface SiteFindingRepository : JpaRepository<SiteFinding, Long> {
    fun findByProjectId(projectId: Long): List<SiteFinding>

    /** 점검 결과로 상태를 바꿀 때 사람의 상태 변경과 한 줄로 세운다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SiteFinding f where f.project.id = :projectId")
    fun findByProjectIdForUpdate(@Param("projectId") projectId: Long): List<SiteFinding>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SiteFinding f where f.id = :id")
    fun findForUpdate(@Param("id") id: Long): SiteFinding?
}

interface SiteFindingStatusHistoryRepository : JpaRepository<SiteFindingStatusHistory, Long>
