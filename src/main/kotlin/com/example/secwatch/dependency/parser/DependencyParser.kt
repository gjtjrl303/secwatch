package com.example.secwatch.dependency.parser

import com.example.secwatch.common.PackageReference

data class ParseResult(val packages: List<PackageReference>, val skipped: Int)

interface DependencyParser {
    fun parse(content: String): ParseResult
}
