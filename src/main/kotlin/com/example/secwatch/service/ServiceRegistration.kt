package com.example.secwatch.service

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServiceRegistration(private val repository: MonitoredServiceRepository) {

    @Transactional
    fun register(name: String, rawDomain: String): MonitoredService {
        val serviceName = name.trim()
        if (serviceName.isEmpty() || serviceName.length > MAX_NAME_LENGTH) throw SecwatchException(ErrorCode.INVALID_NAME)
        val domain = DomainName.normalize(rawDomain)
        if (repository.existsByDomain(domain)) throw SecwatchException(ErrorCode.DUPLICATE_DOMAIN)
        try {
            return repository.save(MonitoredService(serviceName, domain))
        } catch (e: DataIntegrityViolationException) {
            if (!isDuplicateDomain(e)) throw e
            throw SecwatchException(ErrorCode.DUPLICATE_DOMAIN, e)
        }
    }

    private fun isDuplicateDomain(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it.message?.contains(DOMAIN_UNIQUE_KEY) == true }

    private companion object {
        const val MAX_NAME_LENGTH = 100
        const val DOMAIN_UNIQUE_KEY = "uk_monitored_service_domain"
    }
}
