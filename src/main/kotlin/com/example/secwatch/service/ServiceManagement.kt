package com.example.secwatch.service

import com.example.secwatch.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServiceManagement(private val repository: MonitoredServiceRepository) {

    @Transactional(readOnly = true)
    fun list(): List<MonitoredService> = repository.findAll()

    @Transactional(readOnly = true)
    fun get(id: Long): MonitoredService =
        repository.findById(id).orElseThrow { NotFoundException("서비스를 찾을 수 없습니다.") }

    @Transactional
    fun changeWebhook(id: Long, rawUrl: String) {
        val url = WebhookUrl.validate(rawUrl)
        val service = get(id)
        service.webhookUrl = url
        repository.save(service)
    }
}
