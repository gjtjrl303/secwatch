package com.example.secwatch.sitescan

import com.example.secwatch.project.Project
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

enum class SiteScanStatus { RUNNING, SUCCEEDED, FAILED }

// 지금은 응답만 살펴보는 기본 점검뿐이다. API 점검과 전체 점검을 넣을 때 여기에 더한다.
enum class SiteScanType { BASELINE }

@Entity
@DynamicUpdate
@Table(name = "site_scan")
class SiteScan(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scan_type", nullable = false, length = 20)
    val scanType: SiteScanType,

    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var status: SiteScanStatus = SiteScanStatus.RUNNING
        protected set

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null
        protected set

    @Column(name = "error_message")
    var errorMessage: String? = null
        protected set

    @Column(name = "raw_json", length = 16_777_215)
    var rawJson: String? = null
        protected set

    fun succeed(rawJson: String, finishedAt: LocalDateTime) {
        status = SiteScanStatus.SUCCEEDED
        this.rawJson = rawJson
        this.finishedAt = finishedAt
    }

    fun fail(errorMessage: String, finishedAt: LocalDateTime) {
        status = SiteScanStatus.FAILED
        this.errorMessage = errorMessage.take(MAX_ERROR_LENGTH)
        this.finishedAt = finishedAt
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 255
    }
}
