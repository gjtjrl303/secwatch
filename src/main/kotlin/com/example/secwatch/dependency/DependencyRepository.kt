package com.example.secwatch.dependency

import org.springframework.data.jpa.repository.JpaRepository

interface DependencyRepository : JpaRepository<Dependency, Long> {
    fun findByServiceId(serviceId: Long): List<Dependency>
}
