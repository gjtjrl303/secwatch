package com.example.secwatch.dependency.parser

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.SecwatchException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLDecoder
import org.springframework.stereotype.Component

/**
 * CycloneDX SBOM(JSON)의 구성 요소를 읽는다. 구성 요소의 purl(`pkg:종류/네임스페이스/이름@버전?한정자#하위경로`)을
 * OSV가 쓰는 에코시스템 이름과 패키지 이름으로 바꾼다. 바꾸는 규칙을 모르는 종류는 건너뛴다.
 */
@Component
class CycloneDxParser(private val objectMapper: ObjectMapper) : DependencyParser {

    override fun parse(content: String): ParseResult {
        val root = try {
            objectMapper.readTree(content)
        } catch (e: JsonProcessingException) {
            throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)
        }

        val packageReferences = LinkedHashSet<PackageReference>()
        var skipped = 0
        // 프로젝트 자신(metadata.component)은 라이브러리가 아니라서 빼고, 그 안에 든 구성 요소만 읽는다
        val insideProject = root?.get("metadata")?.get("component")?.get(COMPONENTS)
        for (component in allComponents(root?.get(COMPONENTS)) + allComponents(insideProject)) {
            val reference = component.get("purl")?.takeIf { node -> node.isTextual }?.let { node -> toPackageReference(node.asText()) }
            if (reference == null) skipped++ else packageReferences.add(reference)
        }
        if (packageReferences.isEmpty()) throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)
        return ParseResult(packageReferences.toList(), skipped)
    }

    /** 구성 요소는 다른 구성 요소를 품을 수 있어서, 안쪽까지 모두 펼친다. */
    private fun allComponents(components: JsonNode?): List<JsonNode> {
        if (components == null || !components.isArray) return emptyList()
        return components.flatMap { component -> listOf(component) + allComponents(component.get(COMPONENTS)) }
    }

    private fun toPackageReference(purl: String): PackageReference? {
        if (!purl.startsWith(SCHEME, ignoreCase = true)) return null
        val body = purl.substring(SCHEME.length).trimStart('/').substringBefore('#').substringBefore('?')

        val versionStart = body.lastIndexOf('@')
        if (versionStart <= body.lastIndexOf('/')) return null
        val version = decode(body.substring(versionStart + 1)) ?: return null

        val segments = body.substring(0, versionStart).split('/').filter { segment -> segment.isNotEmpty() }
        if (segments.size < 2) return null
        val type = segments.first().lowercase()
        val path = segments.drop(1).map { segment -> decode(segment) ?: return null }
        val namespace = path.dropLast(1).joinToString("/")
        val name = path.last()

        val reference = when (type) {
            "maven" -> if (namespace.isEmpty()) null else PackageReference("Maven", "$namespace:$name", version)
            "npm" -> PackageReference("npm", if (namespace.isEmpty()) name else "$namespace/$name", version)
            "pypi" -> PackageReference("PyPI", name.lowercase().replace(PYPI_SEPARATORS, "-"), version)
            "cargo" -> PackageReference("crates.io", name, version)
            else -> null
        }
        return reference?.takeIf { it.version.isNotBlank() && it.fitsColumns() }
    }

    /** purl은 `+`를 공백으로 쓰지 않으므로, 폼 방식 디코더가 공백으로 바꾸지 않게 먼저 인코딩해 둔다. */
    private fun decode(value: String): String? = try {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val COMPONENTS = "components"
        const val SCHEME = "pkg:"

        val PYPI_SEPARATORS = Regex("[-_.]+")
    }
}
