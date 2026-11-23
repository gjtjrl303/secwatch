package com.example.secwatch.dependency.parser

import com.example.secwatch.common.PackageReference

data class ParseResult(val packages: List<PackageReference>, val skipped: Int)

interface DependencyParser {
    fun parse(content: String): ParseResult
}

// dependency 테이블의 name(255자), version(100자) 컬럼 길이
fun PackageReference.fitsColumns(): Boolean = name.length <= 255 && version.length <= 100
