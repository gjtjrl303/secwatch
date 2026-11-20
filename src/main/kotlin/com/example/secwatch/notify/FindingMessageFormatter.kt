package com.example.secwatch.notify

import com.example.secwatch.finding.Finding

object FindingMessageFormatter {
    private const val MAX_MESSAGE_LENGTH = 2000
    private const val MAX_LINE_LENGTH = 300
    private const val REMAINDER_RESERVE = 20

    /** 디스코드 한 통의 길이 제한 안에서 위험도 순으로 담고, 못 담은 것은 건수만 알린다. */
    fun format(projectName: String, findings: List<Finding>): String {
        val message = StringBuilder("[secwatch] $projectName: 새 취약점 ${findings.size}건")
        var shownCount = 0
        for (finding in findings) {
            val line = "\n" + lineOf(finding)
            if (message.length + line.length + REMAINDER_RESERVE > MAX_MESSAGE_LENGTH) break
            message.append(line)
            shownCount++
        }
        if (shownCount < findings.size) message.append("\n…외 ${findings.size - shownCount}건")
        return message.toString()
    }

    private fun lineOf(finding: Finding): String {
        val dependency = finding.dependency
        val fixedVersions = finding.fixedVersions.ifBlank { "없음" }
        val text = "- [${finding.severity.label}] ${dependency.name}@${dependency.version} — ${finding.summary} (고쳐진 버전: $fixedVersions)"
        return if (text.length > MAX_LINE_LENGTH) text.take(MAX_LINE_LENGTH - 1) + "…" else text
    }
}
