package com.example.secwatch.finding

enum class FindingStatus(val label: String) {
    NEW("새로 발견"),
    ACKNOWLEDGED("확인함"),
    RESOLVED("해결함"),
    NOT_APPLICABLE("해당 없음");

    companion object {
        /** 아직 처리가 끝나지 않은 상태다. */
        val OPEN = listOf(NEW, ACKNOWLEDGED)
    }
}
