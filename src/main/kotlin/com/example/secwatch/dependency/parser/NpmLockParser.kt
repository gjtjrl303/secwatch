package com.example.secwatch.dependency.parser

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.SecwatchException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class NpmLockParser(private val objectMapper: ObjectMapper) : DependencyParser {

    override fun parse(content: String): ParseResult {
        val root = try {
            objectMapper.readTree(content)
        } catch (e: JsonProcessingException) {
            throw unsupported()
        }
        val entries = root?.get("packages")
        if (entries == null || !entries.isObject) throw unsupported()

        val packageReferences = LinkedHashSet<PackageReference>()
        var skipped = 0
        for ((path, node) in entries.fields()) {
            if (path.isEmpty()) continue
            val version = node.get("version")?.asText()
            val isLibrary = path.contains(NODE_MODULES) && !node.path("link").asBoolean(false) && !version.isNullOrBlank()
            if (!isLibrary) {
                skipped++
                continue
            }
            val nameNode = node.get("name")
            if (nameNode != null && !nameNode.isTextual) throw unsupported()
            val name = nameNode?.asText()?.takeIf { it.isNotBlank() } ?: path.substringAfterLast(NODE_MODULES)
            packageReferences.add(PackageReference(ECOSYSTEM, name, version!!))
        }
        if (packageReferences.isEmpty()) throw unsupported()
        return ParseResult(packageReferences.toList(), skipped)
    }

    private fun unsupported() =
        SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)

    private companion object {
        const val ECOSYSTEM = "npm"
        const val NODE_MODULES = "node_modules/"
    }
}
