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
        project.changeWebhook(url)
        projectRepository.save(project)
    }

    @Transactional
    fun changeSiteScanPath(id: Long, rawPath: String) {
        val path = SiteScanSettings.path(rawPath)
        val project = get(id)
        project.changeSiteScanPath(path)
        projectRepository.save(project)
    }

    @Transactional
    fun changeSiteCredentials(id: Long, username: String, password: String) {
        val credentials = SiteScanSettings.credentials(username, password)
        val project = lockedProject(id)
        project.changeSiteCredentials(credentials)
        projectRepository.save(project)
    }

    @Transactional
    fun clearSiteCredentials(id: Long) {
        val project = lockedProject(id)
        project.clearSiteCredentials()
        projectRepository.save(project)
    }

    private fun lockedProject(id: Long): Project =
        projectRepository.findForUpdate(id) ?: throw SecwatchException(ErrorCode.NOT_FOUND_PROJECT)
}
