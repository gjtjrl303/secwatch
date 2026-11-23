package com.example.secwatch.dependency.parser

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.SecwatchException
import org.springframework.stereotype.Component

@Component
class GradleDependenciesParser : DependencyParser {

    override fun parse(content: String): ParseResult {
        val treeLines = runtimeTreeLines(content)

        val packageReferences = LinkedHashSet<PackageReference>()
        var skipped = 0
        for (line in treeLines) {
            when (val read = readLine(line)) {
                Ignored -> Unit
                Unreadable -> skipped++
                is Read -> packageReferences.add(read.reference)
            }
        }
        if (packageReferences.isEmpty()) throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)
        return ParseResult(packageReferences.toList(), skipped)
    }

    /** 전체 출력에는 컴파일용과 테스트용 구성이 섞여 있어서, runtimeClasspath가 있으면 그 구성의 줄만 고른다. */
    private fun runtimeTreeLines(content: String): List<String> {
        val linesByConfiguration = LinkedHashMap<String?, MutableList<String>>()
        var configuration: String? = null
        for (line in content.lineSequence()) {
            val tree = TREE_LINE.matchEntire(line)
            if (tree != null) {
                linesByConfiguration.getOrPut(configuration) { mutableListOf() }.add(tree.groupValues[1].trim())
            } else {
                CONFIGURATION_HEADER.matchEntire(line)?.let { header -> configuration = header.groupValues[1] }
            }
        }
        return linesByConfiguration[RUNTIME_CLASSPATH] ?: linesByConfiguration.values.flatten()
    }

    private fun readLine(line: String): LineResult {
        if (line.startsWith(PROJECT_PREFIX)) return Ignored
        val marker = MARKER.find(line)?.groupValues?.get(1)
        // (c)는 다른 줄에 실제 의존성이 있는 제약이고, (n)은 버전이 정해지지 않은 선언이다
        if (marker == "(c)" || marker == "(n)") return Ignored
        if (marker == "FAILED") return Unreadable

        val body = line.replace(MARKER, "")
        val requested = body.substringBefore(ARROW).split(":", limit = 3)
        val resolved = body.substringAfter(ARROW, "").trim().takeIf { it.isNotEmpty() }
        if (requested.size < 2) return Unreadable

        val replaced = resolved?.split(":")?.takeIf { it.size == 3 }
        val group = replaced?.get(0) ?: requested[0]
        val name = replaced?.get(1) ?: requested[1]
        val version = replaced?.get(2) ?: resolved ?: requested.getOrNull(2)

        if (group.isBlank() || name.isBlank() || version.isNullOrBlank() || version.any { it in NOT_A_VERSION }) return Unreadable
        val reference = PackageReference(ECOSYSTEM, "$group:$name", version)
        return if (reference.fitsColumns()) Read(reference) else Unreadable
    }

    private sealed interface LineResult
    private data object Ignored : LineResult
    private data object Unreadable : LineResult
    private data class Read(val reference: PackageReference) : LineResult

    private companion object {
        const val ECOSYSTEM = "Maven"
        const val RUNTIME_CLASSPATH = "runtimeClasspath"
        const val PROJECT_PREFIX = "project "
        const val ARROW = " -> "
        const val NOT_A_VERSION = " :{}"

        val TREE_LINE = Regex("""[| ]*[+\\]--- (.+)""")
        val CONFIGURATION_HEADER = Regex("""([A-Za-z][\w-]*)( - .*| \(n\))?""")
        val MARKER = Regex("""\s+(\(\*\)|\(c\)|\(n\)|FAILED)\s*$""")
    }
}
