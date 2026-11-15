package com.example.secwatch.dependency

import com.example.secwatch.common.PackageReference
import com.example.secwatch.project.Project
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
@Table(name = "dependency")
class Dependency(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(nullable = false)
    val ecosystem: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val version: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun toPackageReference() = PackageReference(ecosystem, name, version)
}
