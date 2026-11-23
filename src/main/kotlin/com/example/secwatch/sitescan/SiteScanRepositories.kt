package com.example.secwatch.sitescan

import com.example.secwatch.project.Project
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    fun findFirstByProjectIdAndStatusOrderByStartedAtDesc(projectId: Long, status: SiteScanStatus): SiteScan?

    fun findByProjectIdOrderByStartedAtDesc(projectId: Long): List<SiteScan>
}

interface SiteFindingRepository : JpaRepository<SiteFinding, Long> {
    fun findByProjectId(projectId: Long): List<SiteFinding>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SiteFinding f where f.id = :id")
    fun findForUpdate(@Param("id") id: Long): SiteFinding?
}

interface SiteFindingStatusHistoryRepository : JpaRepository<SiteFindingStatusHistory, Long>
