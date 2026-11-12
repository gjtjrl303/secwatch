package com.example.secwatch.service

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "monitored_service")
class MonitoredService(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var domain: String,

    @Column(name = "webhook_url")
    var webhookUrl: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
