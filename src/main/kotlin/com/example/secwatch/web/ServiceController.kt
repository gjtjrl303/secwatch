package com.example.secwatch.web

import com.example.secwatch.service.MonitoredService
import com.example.secwatch.service.ServiceManagement
import com.example.secwatch.service.ServiceRegistration
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class RegisterServiceRequest(val name: String, val domain: String)

data class ServiceResponse(val id: Long, val name: String, val domain: String)

@RestController
@RequestMapping("/api/services")
class ServiceController(
    private val management: ServiceManagement,
    private val registration: ServiceRegistration,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterServiceRequest): ServiceResponse =
        registration.register(request.name, request.domain).toResponse()

    @GetMapping
    fun list(): List<ServiceResponse> = management.list().map { it.toResponse() }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ServiceResponse = management.get(id).toResponse()

    private fun MonitoredService.toResponse() = ServiceResponse(id!!, name, domain)
}
