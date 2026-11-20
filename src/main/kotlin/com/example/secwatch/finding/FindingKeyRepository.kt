package com.example.secwatch.finding

import com.example.secwatch.dependency.Dependency
import org.springframework.data.jpa.repository.JpaRepository

interface FindingKeyRepository : JpaRepository<FindingKey, Long> {
    fun findByDependencyAndVulnerabilityKeyIn(dependency: Dependency, vulnerabilityKeys: Collection<String>): List<FindingKey>
}
