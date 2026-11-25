package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("사이트 점검 시작 경로와 인증 정보")
class SiteScanSettingsTest {
    private fun pathError(raw: String) = assertThrows<SecwatchException> { SiteScanSettings.path(raw) }.errorCode
    private fun credentialsError(username: String, password: String) =
        assertThrows<SecwatchException> { SiteScanSettings.credentials(username, password) }.errorCode

    @Test
    @DisplayName("시작 경로를 비우면 첫 화면부터 점검한다")
    fun blank_path_means_root() {
        assertThat(SiteScanSettings.path("")).isNull()
        assertThat(SiteScanSettings.path("   ")).isNull()
        assertThat(SiteScanSettings.path("/")).isNull()
    }

    @Test
    @DisplayName("시작 경로는 앞뒤 공백을 지우고 그대로 쓴다")
    fun path_is_trimmed() {
        assertThat(SiteScanSettings.path(" /app ")).isEqualTo("/app")
        assertThat(SiteScanSettings.path("/app/board?page=1")).isEqualTo("/app/board?page=1")
    }

    @Test
    @DisplayName("/로 시작하지 않거나 다른 주소로 바뀔 수 있는 경로는 거부한다")
    fun path_that_could_leave_the_domain_is_rejected() {
        listOf("app", "//evil.example.com", "/a b", "/x\ny", "https://evil.example.com", "/redirect?to=https://evil.example.com", "/" + "a".repeat(200))
            .forEach { raw -> assertThat(pathError(raw)).isEqualTo(ErrorCode.INVALID_SITE_SCAN_PATH) }
    }

    @Test
    @DisplayName("아이디와 비밀번호는 둘 다 있어야 한다")
    fun username_and_password_are_required() {
        assertThat(credentialsError("", "pw")).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS)
        assertThat(credentialsError("user", "")).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS)
    }

    @Test
    @DisplayName("Basic 인증 아이디에는 콜론을 넣을 수 없고, 너무 긴 값은 거부한다")
    fun invalid_credentials_are_rejected() {
        assertThat(credentialsError("a:b", "pw")).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS)
        assertThat(credentialsError("u\nser", "pw")).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS)
        assertThat(credentialsError("a".repeat(101), "pw")).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS)
        assertThat(credentialsError("user", "p".repeat(101))).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS)
    }

    @Test
    @DisplayName("점검할 때 아이디와 비밀번호를 둘 다 비우면 인증 없이 점검하고, 하나만 넣으면 거부한다")
    fun optional_credentials_at_scan_time() {
        assertThat(SiteScanSettings.optionalCredentials("", "")).isNull()
        assertThat(SiteScanSettings.optionalCredentials("  ", "")).isNull()
        assertThat(SiteScanSettings.optionalCredentials("user", "pw")?.password).isEqualTo("pw")
        listOf("user" to "", "" to "pw").forEach { (username, password) ->
            assertThatThrownBy { SiteScanSettings.optionalCredentials(username, password) }
                .isInstanceOfSatisfying(SecwatchException::class.java) { assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_SITE_CREDENTIALS) }
        }
    }

    @Test
    @DisplayName("아이디의 앞뒤 공백은 지우고 비밀번호는 그대로 둔다")
    fun username_is_trimmed_password_is_kept() {
        val credentials = SiteScanSettings.credentials(" user ", " p:w$ ")
        assertThat(credentials.username).isEqualTo("user")
        assertThat(credentials.password).isEqualTo(" p:w$ ")
    }

    @Test
    @DisplayName("인증 정보를 문자열로 바꿔도 비밀번호는 나오지 않는다")
    fun toString_hides_the_password() {
        assertThat(SiteCredentials("user", "topsecret").toString()).doesNotContain("topsecret")
    }
}
