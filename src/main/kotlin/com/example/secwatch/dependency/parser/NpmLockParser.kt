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
            throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)
        }
        val entries = root?.get("packages")
        if (entries == null || !entries.isObject) throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)

        val packageReferences = LinkedHashSet<PackageReference>()
        var skipped = 0
        for ((path, node) in entries.fields()) {
            if (path.isEmpty()) continue
            val version = node.get("version")?.asText()
            val isInNodeModules = path.contains(NODE_MODULES)
            val isLink = node.path("link").asBoolean(false)
            if (!isInNodeModules || isLink || version.isNullOrBlank()) {
                skipped++
                continue
            }

            val nameNode = node.get("name")
            if (nameNode != null && !nameNode.isTextual) throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)
            val nameFromPath = path.substringAfterLast(NODE_MODULES)
            val name = if (nameNode != null && nameNode.asText().isNotBlank()) nameNode.asText() else nameFromPath
            packageReferences.add(PackageReference(ECOSYSTEM, name, version))
        }
        if (packageReferences.isEmpty()) throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)
        return ParseResult(packageReferences.toList(), skipped)
    }

    private companion object {
        const val ECOSYSTEM = "npm"
        const val NODE_MODULES = "node_modules/"
    }
}
