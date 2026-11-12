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
