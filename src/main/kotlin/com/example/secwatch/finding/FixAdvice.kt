package com.example.secwatch.finding

object FixAdvice {
    fun text(currentVersion: String, fixedVersions: List<String>): String {
        if (fixedVersions.isEmpty()) return "아직 고쳐진 버전이 없습니다. 원문에서 임시 대응 방법을 확인해 주세요."

        val current = numbers(currentVersion)
        val nearestHigher = fixedVersions
            .map { version -> version to numbers(version) }
            .filter { (_, parts) -> current.isNotEmpty() && parts.isNotEmpty() && compare(parts, current) > 0 }
            .minWithOrNull { left, right -> compare(left.second, right.second) }
            ?.first
        return if (nearestHigher != null) {
            "$nearestHigher 이상으로 올립니다."
        } else {
            "고쳐진 버전(${fixedVersions.joinToString(", ")}) 중 하나로 바꿉니다."
        }
    }

    // "4.1.118.Final" → [4, 1, 118]. 앞에서부터 숫자인 부분만 쓴다.
    private fun numbers(version: String): List<Int> =
        version.split('.', '-', '_').map { part -> part.toIntOrNull() }.takeWhile { it != null }.filterNotNull()

    private fun compare(left: List<Int>, right: List<Int>): Int {
        for (index in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }
}
