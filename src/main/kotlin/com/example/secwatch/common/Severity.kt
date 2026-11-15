package com.example.secwatch.common

enum class Severity(val label: String) {
    CRITICAL("치명"),
    HIGH("높음"),
    MODERATE("보통"),
    LOW("낮음"),
    UNCLASSIFIED("미분류");

    companion object {
        fun fromOsv(value: String?): Severity {
            val matched = entries.firstOrNull { severity -> severity.name == value?.uppercase() }
            return matched ?: UNCLASSIFIED
        }
    }
}
