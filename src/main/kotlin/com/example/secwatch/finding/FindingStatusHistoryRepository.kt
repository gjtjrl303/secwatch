package com.example.secwatch.finding

import org.springframework.data.jpa.repository.JpaRepository

interface FindingStatusHistoryRepository : JpaRepository<FindingStatusHistory, Long>
