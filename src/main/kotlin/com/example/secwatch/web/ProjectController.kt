package com.example.secwatch.web

import com.example.secwatch.common.ApiSuccessResponse
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectManagement
import com.example.secwatch.project.ProjectRegistration
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class RegisterProjectRequest(val name: String, val domain: String)

data class ProjectResponse(val id: Long, val name: String, val domain: String)

@RestController
@RequestMapping("/api/projects")
class ProjectController(
    private val projectManagement: ProjectManagement,
    private val projectRegistration: ProjectRegistration,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterProjectRequest): ApiSuccessResponse<ProjectResponse> =
        ApiSuccessResponse(projectRegistration.register(request.name, request.domain).toResponse())

    @GetMapping
    fun list(): ApiSuccessResponse<List<ProjectResponse>> =
        ApiSuccessResponse(projectManagement.list().map { it.toResponse() })

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ApiSuccessResponse<ProjectResponse> =
        ApiSuccessResponse(projectManagement.get(id).toResponse())

    private fun Project.toResponse() = ProjectResponse(id!!, name, domain)
}
