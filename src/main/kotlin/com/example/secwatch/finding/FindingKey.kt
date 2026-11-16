package com.example.secwatch.finding

import com.example.secwatch.dependency.Dependency
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "finding_key")
class FindingKey(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id", nullable = false)
    val finding: Finding,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependency_id", nullable = false)
    val dependency: Dependency,

    @Column(name = "vulnerability_key", nullable = false)
    val vulnerabilityKey: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
