package com.example.secwatch.dependency.parser

import com.example.secwatch.common.PackageRef

data class ParseResult(val packages: List<PackageRef>, val skipped: Int)

interface DependencyParser {
    fun parse(content: String): ParseResult
}
