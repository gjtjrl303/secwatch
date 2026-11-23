package com.example.secwatch.sitescan

import com.example.secwatch.finding.FindingStatus
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

@Entity
@DynamicUpdate
@Table(name = "site_finding")
class SiteFinding(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    alert: SiteAlert,

    @Column(name = "first_seen_at", nullable = false)
    val firstSeenAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "alert_ref", nullable = false, length = 50)
    val alertRef: String = alert.alertRef

    @Column(nullable = false)
    var name: String = ""
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var risk: SiteRisk = alert.risk
        protected set

    @Column(nullable = false, length = 65_535)
    var description: String = ""
        protected set

    @Column(nullable = false, length = 65_535)
    var solution: String = ""
        protected set

    @Column(nullable = false, length = 65_535)
    var reference: String = ""
        protected set

    @Column(name = "cwe_id")
    var cweId: Int? = null
        protected set

    @Column(name = "instance_count", nullable = false)
    var instanceCount: Int = 0
        protected set

    @Column(name = "sample_urls", nullable = false, length = 65_535)
    var sampleUrls: String = ""
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var status: FindingStatus = FindingStatus.NEW
        protected set

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: LocalDateTime = firstSeenAt
        protected set

    init {
        seenAgain(alert, firstSeenAt)
    }

    fun seenAgain(alert: SiteAlert, seenAt: LocalDateTime) {
        name = alert.name.take(MAX_NAME_LENGTH)
        risk = alert.risk
        description = alert.description.take(MAX_TEXT_LENGTH)
        solution = alert.solution.take(MAX_TEXT_LENGTH)
        reference = alert.reference.take(MAX_TEXT_LENGTH)
        cweId = alert.cweId
        instanceCount = alert.urls.size
        sampleUrls = alert.urls.take(MAX_SAMPLE_URLS).joinToString("\n").take(MAX_TEXT_LENGTH)
        lastSeenAt = seenAt
    }

    fun changeStatus(newStatus: FindingStatus) {
        status = newStatus
    }

    private companion object {
        const val MAX_NAME_LENGTH = 255
        const val MAX_SAMPLE_URLS = 10
        // TEXT 컬럼은 65,535바이트까지라서 한글(글자당 3바이트)이어도 넘지 않게 잡는다
        const val MAX_TEXT_LENGTH = 20_000
    }
}
