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
        val domain = DomainName.normalize(rawDomain)
        val duplicate = SecwatchException(ErrorCode.DUPLICATE_DOMAIN)
        if (repository.existsByDomain(domain)) throw duplicate
        try {
            return repository.save(MonitoredService(name, domain))
        } catch (e: DataIntegrityViolationException) {
            throw duplicate
        }
    }
}
