package com.example.secwatch.dependency

import com.example.secwatch.common.PackageRef
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
    var projectId: Long,

    @Column(nullable = false)
    var ecosystem: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var version: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    fun toRef() = PackageRef(ecosystem, name, version)
}
