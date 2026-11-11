package com.example.secwatch.service

import com.example.secwatch.common.ConflictException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServiceRegistration(private val repository: MonitoredServiceRepository) {

    @Transactional
    fun register(name: String, rawDomain: String): MonitoredService {
        val domain = DomainName.normalize(rawDomain)
        val duplicate = ConflictException("이미 등록된 도메인입니다.")
        if (repository.existsByDomain(domain)) throw duplicate
        try {
            return repository.save(MonitoredService(name, domain))
        } catch (e: DataIntegrityViolationException) {
            throw duplicate
        }
    }
}
