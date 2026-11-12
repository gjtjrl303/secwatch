package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectManagement(private val projectRepository: ProjectRepository) {

    @Transactional(readOnly = true)
    fun list(): List<Project> = projectRepository.findAll()

    @Transactional(readOnly = true)
    fun get(id: Long): Project =
        projectRepository.findById(id).orElseThrow { SecwatchException(ErrorCode.NOT_FOUND_PROJECT) }

    @Transactional
    fun changeWebhook(id: Long, rawUrl: String) {
        val url = WebhookUrl.validate(rawUrl)
        val project = get(id)
        project.webhookUrl = url
        projectRepository.save(project)
    }
}
