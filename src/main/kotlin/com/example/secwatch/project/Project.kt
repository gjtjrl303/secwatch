package com.example.secwatch.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.DynamicUpdate

@Entity
@DynamicUpdate
@Table(name = "project")
class Project(name: String, domain: String) {

    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false)
    var domain: String = domain
        protected set

    @Column(name = "webhook_url")
    var webhookUrl: String? = null
        protected set

    @Column(name = "last_checked_at")
    var lastCheckedAt: LocalDateTime? = null
        protected set

    @Column(name = "last_check_error")
    var lastCheckError: String? = null
        protected set

    @Column(name = "last_notify_error")
    var lastNotifyError: String? = null
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun update(name: String, domain: String) {
        this.name = name
        this.domain = domain
    }

    fun changeWebhook(webhookUrl: String) {
        this.webhookUrl = webhookUrl
    }

    fun markChecked(checkedAt: LocalDateTime) {
        lastCheckedAt = checkedAt
        lastCheckError = null
    }

    fun markCheckFailed(errorMessage: String) {
        lastCheckError = errorMessage
    }

    fun markNotifySucceeded() {
        lastNotifyError = null
    }

    fun markNotifyFailed(errorMessage: String) {
        lastNotifyError = errorMessage
    }
}
