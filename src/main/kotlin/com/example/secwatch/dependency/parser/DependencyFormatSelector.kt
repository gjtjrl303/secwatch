package com.example.secwatch.dependency.parser

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class DependencyFormatSelector(
    private val objectMapper: ObjectMapper,
    private val npmLockParser: NpmLockParser,
    private val cycloneDxParser: CycloneDxParser,
    private val gradleDependenciesParser: GradleDependenciesParser,
) {
    fun parse(content: String): ParseResult {
        val root = readJsonObject(content) ?: return gradleDependenciesParser.parse(content)
        val isCycloneDx = root.path("bomFormat").asText() == CYCLONE_DX
        return if (isCycloneDx) cycloneDxParser.parse(content) else npmLockParser.parse(content)
    }

    private fun readJsonObject(content: String): JsonNode? = try {
        objectMapper.readTree(content)?.takeIf { root -> root.isObject }
    } catch (e: JsonProcessingException) {
        null
    }

    private companion object {
        const val CYCLONE_DX = "CycloneDX"
    }
}
