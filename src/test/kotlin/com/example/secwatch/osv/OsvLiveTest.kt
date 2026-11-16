package com.example.secwatch.osv

import com.example.secwatch.common.PackageReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.web.client.RestClient

@EnabledIfEnvironmentVariable(named = "OSV_LIVE", matches = "1")
@DisplayName("OSV 실제 호출 (OSV_LIVE=1일 때만 실행)")
class OsvLiveTest {
    private val client = OsvClient(RestClient.builder().baseUrl("https://api.osv.dev").build())

    @Test
    @DisplayName("lodash 4.17.20의 알려진 취약점과 상세를 실제 OSV에서 받아 온다")
    fun lodash_4_17_20_has_known_vulnerabilities_with_details() {
        val packageReference = PackageReference("npm", "lodash", "4.17.20")

        val ids = client.findVulnerabilityIds(listOf(packageReference)).getValue(packageReference)
        assertThat(ids).isNotEmpty()

        val first = ids.mapNotNull { id -> client.getVulnerability(id) }.first()
        assertThat(first.summary).isNotBlank()
        assertThat(first.referenceUrl).startsWith("https://osv.dev/vulnerability/")
    }
}
