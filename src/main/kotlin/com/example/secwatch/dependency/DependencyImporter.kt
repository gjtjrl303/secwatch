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
    private val parser: DependencyParser,
    private val projects: ProjectRepository,
    private val dependencies: DependencyRepository,
    private val publisher: ApplicationEventPublisher,
) {
    @Transactional
    fun import(projectId: Long, content: String): ImportResult {
        if (content.isBlank()) throw SecwatchException(ErrorCode.EMPTY_DEPENDENCIES)
        projects.findForUpdate(projectId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_PROJECT)

        val parsed = parser.parse(content)
        val wanted = parsed.packages.toSet()
        val existing = dependencies.findByProjectId(projectId)
        val kept = existing.map { it.toRef() }.toSet()

        dependencies.deleteAll(existing.filter { it.toRef() !in wanted })
        dependencies.saveAll(
            wanted.filter { it !in kept }.map { Dependency(projectId, it.ecosystem, it.name, it.version) }
        )
        publisher.publishEvent(DependenciesImported(projectId))
        return ImportResult(wanted.size, parsed.skipped)
    }
}
