package com.example.secwatch.service

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class WebhookRequest(val webhookUrl: String = "")

@RestController
@RequestMapping("/api/services/{id}/notification")
class NotificationController(private val management: ServiceManagement) {

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(@PathVariable id: Long, @RequestBody request: WebhookRequest) {
        management.changeWebhook(id, request.webhookUrl)
    }
}
