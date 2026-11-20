package com.example.secwatch.finding

import org.springframework.data.jpa.repository.JpaRepository

interface FindingRepository : JpaRepository<Finding, Long>
