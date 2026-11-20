package com.example.secwatch.application

import com.example.secwatch.dependency.DependencyImporter
import com.example.secwatch.project.ProjectManagement
import com.example.secwatch.project.ProjectRegistration
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectSetup(
    private val projectRegistration: ProjectRegistration,
    private val projectManagement: ProjectManagement,
    private val dependencyImporter: DependencyImporter,
) {
    /** 프로젝트 만들기와 웹훅 저장과 의존성 가져오기를 한 번에 한다. 하나라도 틀리면 아무것도 만들지 않는다. */
    @Transactional
    fun create(name: String, domain: String, webhookUrl: String, lockfile: String): Long {
        val projectId = projectRegistration.register(name, domain).id!!
        if (webhookUrl.isNotBlank()) projectManagement.changeWebhook(projectId, webhookUrl)
        if (lockfile.isNotBlank()) dependencyImporter.import(projectId, lockfile)
        return projectId
    }
}
