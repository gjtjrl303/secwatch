package com.example.secwatch.dependency

import com.example.secwatch.common.ApiSuccessResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class ImportRequest(val content: String = "")

@RestController
@RequestMapping("/api/projects/{id}/dependencies")
class DependencyController(private val dependencyImporter: DependencyImporter) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun import(@PathVariable id: Long, @RequestBody request: ImportRequest): ApiSuccessResponse<ImportResult> =
        ApiSuccessResponse(dependencyImporter.import(id, request.content))
}
