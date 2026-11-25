package com.example.secwatch.sitescan

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.project.SiteCredentials
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("사이트 점검 대상 주소")
class SiteScanTargetsTest {
    private fun targets(vararg allowed: String) = SiteScanTargets(allowed.toList())

    @Test
    @DisplayName("허용 목록에 있는 localhost는 http 주소로 만든다")
    fun localhost_uses_http() {
        assertThat(targets("localhost").urlFor("localhost:3000")).isEqualTo("http://localhost:3000")
        assertThat(targets("localhost").urlFor("localhost")).isEqualTo("http://localhost")
    }

    @Test
    @DisplayName("허용 목록에 있는 그 밖의 도메인은 https 주소로 만든다")
    fun other_hosts_use_https() {
        assertThat(targets("localhost", "dev.example.com").urlFor("dev.example.com")).isEqualTo("https://dev.example.com")
    }

    @Test
    @DisplayName("시작 경로가 있으면 주소 뒤에 붙인다")
    fun start_path_is_appended() {
        assertThat(targets().urlFor("dev.example.com", "/app")).isEqualTo("https://dev.example.com/app")
        assertThat(targets().urlFor("localhost:3000", "/app?x=1")).isEqualTo("http://localhost:3000/app?x=1")
        assertThat(targets().urlFor("dev.example.com", null)).isEqualTo("https://dev.example.com")
    }

    @Test
    @DisplayName("점검 대상을 문자열로 바꿔도 비밀번호는 나오지 않는다")
    fun target_toString_hides_the_password() {
        val target = SiteScanTarget("https://dev.example.com", SiteCredentials("user", "topsecret"))

        assertThat(target.toString()).contains("https://dev.example.com").doesNotContain("topsecret")
    }

    @Test
    @DisplayName("허용 목록은 대소문자와 앞뒤 공백을 가리지 않는다")
    fun allow_list_is_normalized() {
        assertThat(targets(" Dev.Example.com ").urlFor("dev.example.com:8443")).isEqualTo("https://dev.example.com:8443")
    }

    @Test
    @DisplayName("허용 목록에 없는 도메인은 거부한다")
    fun host_outside_the_allow_list_is_rejected() {
        listOf("example.com", "sub.localhost.example.com", "localhost.example.com:3000", "notlocalhost", "a.localhost:3000").forEach { domain ->
            assertThat(assertThrows<SecwatchException> { targets("localhost").urlFor(domain) }.errorCode)
                .isEqualTo(ErrorCode.SCAN_TARGET_NOT_ALLOWED)
        }
    }

    @Test
    @DisplayName("허용 목록이 비어 있으면 모든 주소를 점검한다")
    fun empty_allow_list_allows_everything() {
        assertThat(targets().urlFor("localhost:3000")).isEqualTo("http://localhost:3000")
        assertThat(targets().urlFor("dev.example.com")).isEqualTo("https://dev.example.com")
        assertThat(targets("", " ").isAllowed("example.com")).isTrue()
    }
}
