package com.example.secwatch.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "project")
class Project(
    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val domain: String,
) {
    @Column(name = "webhook_url")
    var webhookUrl: String? = null
        private set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        private set

    fun changeWebhook(webhookUrl: String) {
        this.webhookUrl = webhookUrl
    }
}
