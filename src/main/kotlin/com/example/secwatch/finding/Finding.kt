package com.example.secwatch.finding

import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.Dependency
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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "finding")
class Finding(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependency_id", nullable = false)
    val dependency: Dependency,

    @Column(name = "primary_vulnerability_id", nullable = false)
    val primaryVulnerabilityId: String,

    @Column(nullable = false, length = 1000)
    val summary: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    val severity: Severity,

    @Column(name = "fixed_versions", nullable = false, length = 1000)
    val fixedVersions: String,

    @Column(name = "reference_url", nullable = false, length = 500)
    val referenceUrl: String,

    @Column(name = "raw_json", nullable = false, length = 16_777_215)
    val rawJson: String,

    @Column(name = "first_seen_at", nullable = false)
    val firstSeenAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var status: FindingStatus = FindingStatus.NEW
        protected set
}
