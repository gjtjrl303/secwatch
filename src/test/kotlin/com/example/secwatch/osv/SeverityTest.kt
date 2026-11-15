package com.example.secwatch.osv

import com.example.secwatch.common.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("위험도")
class SeverityTest {

    @Test
    @DisplayName("OSV의 위험도 문자열을 대소문자 구분 없이 읽는다")
    fun reads_osv_severity_ignoring_case() {
        assertThat(Severity.fromOsv("HIGH")).isEqualTo(Severity.HIGH)
        assertThat(Severity.fromOsv("critical")).isEqualTo(Severity.CRITICAL)
        assertThat(Severity.fromOsv("Moderate")).isEqualTo(Severity.MODERATE)
    }

    @Test
    @DisplayName("없거나 모르는 값은 미분류로 읽는다")
    fun unknown_or_missing_is_unclassified() {
        assertThat(Severity.fromOsv(null)).isEqualTo(Severity.UNCLASSIFIED)
        assertThat(Severity.fromOsv("whatever")).isEqualTo(Severity.UNCLASSIFIED)
    }
}
