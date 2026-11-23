package com.example.secwatch.sitescan

enum class SiteRisk(val label: String) {
    HIGH("높음"),
    MEDIUM("보통"),
    LOW("낮음"),
    INFORMATIONAL("정보"),
}

data class SiteAlert(
    val alertRef: String,
    val name: String,
    val risk: SiteRisk,
    val description: String,
    val solution: String,
    val reference: String,
    val cweId: Int?,
    val urls: List<String>,
)
