package com.example.secwatch.dependency

import com.example.secwatch.common.PackageReference
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "dependency")
class Dependency(
    @Column(name = "project_id", nullable = false)
    val projectId: Long,

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
        private set

    fun toPackageReference() = PackageReference(ecosystem, name, version)
}
