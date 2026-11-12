package com.example.secwatch.dependency

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.dependency.parser.DependencyParser
import com.example.secwatch.project.ProjectRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ImportResult(val parsed: Int, val skipped: Int)

@Service
class DependencyImporter(
    private val dependencyParser: DependencyParser,
    private val projectRepository: ProjectRepository,
    private val dependencyRepository: DependencyRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun import(projectId: Long, content: String): ImportResult {
        if (content.isBlank()) throw SecwatchException(ErrorCode.EMPTY_DEPENDENCIES)
        projectRepository.findForUpdate(projectId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_PROJECT)

        val parsed = dependencyParser.parse(content)
        val requested = parsed.packages.toSet()
        val existing = dependencyRepository.findByProjectId(projectId)
        val existingReferences = existing.map { dependency -> dependency.toPackageReference() }.toSet()

        val removed = existing.filter { dependency -> dependency.toPackageReference() !in requested }
        val added = requested.filter { reference -> reference !in existingReferences }

        dependencyRepository.deleteAll(removed)
        dependencyRepository.saveAll(
            added.map { reference -> Dependency(projectId, reference.ecosystem, reference.name, reference.version) }
        )
        applicationEventPublisher.publishEvent(DependenciesImported(projectId))
        return ImportResult(requested.size, parsed.skipped)
    }
}
