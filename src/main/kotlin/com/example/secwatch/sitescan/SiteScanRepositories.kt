package com.example.secwatch.sitescan

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SiteScanRepository : JpaRepository<SiteScan, Long> {
    fun findByStatus(status: SiteScanStatus): List<SiteScan>

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
