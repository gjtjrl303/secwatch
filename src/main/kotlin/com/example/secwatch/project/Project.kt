package com.example.secwatch.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "project")
class Project(name: String, domain: String) {

    @Column(nullable = false)
    var name: String = name
        private set

    @Column(nullable = false)
    var domain: String = domain
        private set

    @Column(name = "webhook_url")
    var webhookUrl: String? = null
        private set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        private set

    fun update(name: String, domain: String) {
        this.name = name
        this.domain = domain
    }

    fun changeWebhook(webhookUrl: String) {
        this.webhookUrl = webhookUrl
    }
}
