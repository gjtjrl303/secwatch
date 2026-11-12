package com.example.secwatch.dependency

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.dependency.parser.DependencyParser
import com.example.secwatch.service.MonitoredServiceRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ImportResult(val parsed: Int, val skipped: Int)

@Service
class DependencyImporter(
    private val parser: DependencyParser,
    private val services: MonitoredServiceRepository,
    private val dependencies: DependencyRepository,
    private val publisher: ApplicationEventPublisher,
) {
    @Transactional
    fun import(serviceId: Long, content: String): ImportResult {
        if (content.isBlank()) throw SecwatchException(ErrorCode.EMPTY_DEPENDENCIES)
        services.findForUpdate(serviceId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_SERVICE)

        val parsed = parser.parse(content)
        val wanted = parsed.packages.toSet()
        val existing = dependencies.findByServiceId(serviceId)
        val kept = existing.map { it.toRef() }.toSet()

        dependencies.deleteAll(existing.filter { it.toRef() !in wanted })
        dependencies.saveAll(
            wanted.filter { it !in kept }.map { Dependency(serviceId, it.ecosystem, it.name, it.version) }
        )
        publisher.publishEvent(DependenciesImported(serviceId))
        return ImportResult(wanted.size, parsed.skipped)
    }
}
