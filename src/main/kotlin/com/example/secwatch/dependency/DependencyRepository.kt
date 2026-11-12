package com.example.secwatch.dependency

import org.springframework.data.jpa.repository.JpaRepository

interface DependencyRepository : JpaRepository<Dependency, Long> {
    fun findByProjectId(projectId: Long): List<Dependency>
}
