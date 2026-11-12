package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectManagement(private val repository: ProjectRepository) {

    @Transactional(readOnly = true)
    fun list(): List<Project> = repository.findAll()

    @Transactional(readOnly = true)
    fun get(id: Long): Project =
        repository.findById(id).orElseThrow { SecwatchException(ErrorCode.NOT_FOUND_PROJECT) }

    @Transactional
    fun changeWebhook(id: Long, rawUrl: String) {
        val url = WebhookUrl.validate(rawUrl)
        val project = get(id)
        project.webhookUrl = url
        repository.save(project)
    }
}
