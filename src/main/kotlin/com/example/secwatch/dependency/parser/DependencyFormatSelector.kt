package com.example.secwatch.dependency.parser

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/** 붙여넣은 내용이 어떤 형식인지 보고 맞는 파서에 넘긴다. CycloneDX 표시가 없으면 package-lock.json으로 본다. */
@Component
class DependencyFormatSelector(
    private val objectMapper: ObjectMapper,
    private val npmLockParser: NpmLockParser,
    private val cycloneDxParser: CycloneDxParser,
) {
    fun parse(content: String): ParseResult {
        val root = try {
            objectMapper.readTree(content)
        } catch (e: JsonProcessingException) {
            throw SecwatchException(ErrorCode.UNSUPPORTED_FORMAT)
        }
        val isCycloneDx = root?.path("bomFormat")?.asText() == CYCLONE_DX
        return if (isCycloneDx) cycloneDxParser.parse(content) else npmLockParser.parse(content)
    }

    private companion object {
        const val CYCLONE_DX = "CycloneDX"
    }
}
