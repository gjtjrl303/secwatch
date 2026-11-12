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
        val wanted = parsed.packages.toSet()
        val existing = dependencyRepository.findByProjectId(projectId)
        val kept = existing.map { it.toPackageReference() }.toSet()

        dependencyRepository.deleteAll(existing.filter { it.toPackageReference() !in wanted })
        dependencyRepository.saveAll(
            wanted.filter { it !in kept }.map { Dependency(projectId, it.ecosystem, it.name, it.version) }
        )
        applicationEventPublisher.publishEvent(DependenciesImported(projectId))
        return ImportResult(wanted.size, parsed.skipped)
    }
}
