package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectRegistration(private val projectRepository: ProjectRepository) {

    @Transactional
    fun register(name: String, rawDomain: String): Project {
        val projectName = name.trim()
        if (projectName.isEmpty() || projectName.length > MAX_NAME_LENGTH) throw SecwatchException(ErrorCode.INVALID_NAME)
        val domain = DomainName.normalize(rawDomain)
        if (projectRepository.existsByDomain(domain)) throw SecwatchException(ErrorCode.DUPLICATE_DOMAIN)
        try {
            return projectRepository.save(Project(projectName, domain))
        } catch (e: DataIntegrityViolationException) {
            if (!isDuplicateDomain(e)) throw e
            throw SecwatchException(ErrorCode.DUPLICATE_DOMAIN, e)
        }
    }

    private fun isDuplicateDomain(e: Throwable): Boolean {
        val causes = generateSequence(e) { cause -> cause.cause }
        return causes.any { cause -> cause.message?.contains(DOMAIN_UNIQUE_KEY) == true }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 100
        const val DOMAIN_UNIQUE_KEY = "uk_project_domain"
    }
}
