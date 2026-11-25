package com.example.secwatch.sitescan

import com.example.secwatch.project.SiteCredentials
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

@DisplayName("Docker로 ZAP 실행")
class ZapDockerScannerTest {
    @TempDir lateinit var tempDir: Path

    private val calls get() = tempDir.resolve("calls.txt")
    private val planCopy get() = tempDir.resolve("plan-copy.yaml")

    /**
     * docker 대신 부를 스크립트다. 받은 인자를 적어 두고 점검 계획을 복사해 둔 뒤, [body]가 시키는 대로 한다.
     * 스크립트 안에서 dir은 마운트한 폴더, url은 점검 계획에 적힌 주소다.
     */
    private fun fakeDocker(body: String, onRemove: String = "exit 0"): String {
        val script = tempDir.resolve("docker.sh")
        Files.writeString(
            script,
            listOf(
                "#!/bin/sh",
                "echo \"\$@\" >> \"$calls\"",
                "if [ \"\$1\" = \"rm\" ]; then $onRemove; fi",
                "dir=\"\"",
                "while [ \$# -gt 0 ]; do if [ \"\$1\" = \"-v\" ]; then dir=\"\${2%%:*}\"; fi; shift; done",
                "echo \"\$dir\" > \"${tempDir.resolve("workdir.txt")}\"",
                "ls -ld \"\$(dirname \"\$dir\")\" | cut -c1-10 > \"${tempDir.resolve("parent-mode.txt")}\"",
                "cp \"\$dir/plan.yaml\" \"$planCopy\"",
                "url=\$(grep 'url:' \"\$dir/plan.yaml\" | tail -1 | sed \"s/.*url: *//; s/'//g\")",
                body,
            ).joinToString("\n"),
        )
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        return script.toString()
    }

    private fun scanner(body: String, timeout: Duration = Duration.ofSeconds(10), onRemove: String = "exit 0") =
        ZapDockerScanner(fakeDocker(body, onRemove), "zap-image", 1, timeout)

    private val writeReport = "printf '{\"target\":\"%s\"}' \"\$url\" > \"\$dir/report.json\""

    private fun messageOf(action: () -> Unit) = assertThrows<SiteScanFailedException> { action() }.message

    private fun target(url: String, credentials: SiteCredentials? = null) = SiteScanTarget(url, credentials)

    private val credentials = SiteCredentials("user", "topsecret")
    private val basicToken = Base64.getEncoder().encodeToString("user:topsecret".toByteArray())

    @Test
    @DisplayName("둘러보기는 시작 경로에서 시작하고, 점검 범위는 사이트 전체로 둔다")
    fun spider_starts_at_the_path_and_scope_is_the_whole_site() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com/app"))

        val plan = Files.readString(planCopy)
        assertThat(plan).contains("urls:\n    - 'https://dev.example.com'\n")
        assertThat(plan).contains("url: 'https://dev.example.com/app'")
    }

    @Test
    @DisplayName("localhost에 시작 경로가 있으면 호스트만 바꿔 넘긴다")
    fun localhost_with_path_is_translated() {
        scanner("$writeReport; exit 0").scan(target("http://localhost:3000/app"))

        assertThat(Files.readString(planCopy))
            .contains("- 'http://host.docker.internal:3000'\n")
            .contains("url: 'http://host.docker.internal:3000/app'")
    }

    @Test
    @DisplayName("경로에 작은따옴표가 있어도 점검 계획의 문자열이 깨지지 않는다")
    fun quote_in_path_is_escaped() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com/it's"))

        assertThat(Files.readString(planCopy)).contains("url: 'https://dev.example.com/it''s'")
    }

    @Test
    @DisplayName("인증 정보가 없으면 헤더를 붙이는 작업을 넣지 않는다")
    fun no_credentials_no_replacer() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com"))

        assertThat(Files.readString(planCopy)).doesNotContain("replacer").doesNotContain("Authorization")
    }

    @Test
    @DisplayName("인증 정보가 있으면 둘러보기 전에 이 사이트로 가는 요청에만 Basic 인증 헤더를 붙인다")
    fun credentials_add_basic_auth_header_for_this_site_only() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com/app", credentials))

        val plan = Files.readString(planCopy)
        assertThat(plan).contains("type: replacer").contains("matchString: Authorization").contains("replacementString: 'Basic $basicToken'")
        assertThat(plan.indexOf("type: replacer")).isLessThan(plan.indexOf("type: spider"))

        val urlRegex = Regex(Regex("""url: '(\\Q.*)'""").find(plan)!!.groupValues[1])
        assertThat(urlRegex.matches("https://dev.example.com/app")).isTrue()
        assertThat(urlRegex.matches("https://dev.example.com")).isTrue()
        assertThat(urlRegex.matches("https://dev.example.com?x=1")).isTrue()
        assertThat(urlRegex.matches("https://dev.example.com.evil.example.com/")).isFalse()
        assertThat(urlRegex.matches("https://devXexample.com/")).isFalse()
        assertThat(urlRegex.matches("https://cdn.example.com/https://dev.example.com/")).isFalse()
    }

    @Test
    @DisplayName("인증 정보가 있으면 우리가 붙인 Basic 인증 헤더를 문제로 잡는 규칙(10105)을 끈다")
    fun credentials_turn_off_the_captured_credentials_rule() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com", credentials))

        val plan = Files.readString(planCopy)
        assertThat(plan).contains("  rules:\n  - id: 10105\n    threshold: \"off\"\n")
        assertThat(plan.indexOf("id: 10105")).isLessThan(plan.indexOf("type: spider"))
    }

    @Test
    @DisplayName("인증 정보가 없으면 그 규칙을 끄지 않는다")
    fun no_credentials_keep_the_captured_credentials_rule() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com"))

        assertThat(Files.readString(planCopy)).doesNotContain("10105")
    }

    @Test
    @DisplayName("결과에 아이디와 비밀번호, 인증 헤더 값이 남아 있으면 가리고 돌려준다")
    fun credentials_left_in_the_report_are_masked() {
        val password = "p<w>&\"x\\y"
        val token = Base64.getEncoder().encodeToString("user:$password".toByteArray())
        val htmlEscaped = "p&lt;w&gt;&amp;&quot;x\\y"
        fun json(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")
        val report = Files.writeString(
            tempDir.resolve("leaky.json"),
            """{"site":[{"@name":"https://dev.example.com","alerts":[{"name":"n","otherinfo":"<p>revealing username [user] and password [${json(htmlEscaped)}].</p>","instances":[{"uri":"https://dev.example.com/a","evidence":"Authorization: Basic $token","otherinfo":"pair user:${json(password)} raw ${json(password)}"}]}]}]}""",
        )

        val result = scanner("cp \"$report\" \"\$dir/report.json\"; exit 0")
            .scan(target("https://dev.example.com", SiteCredentials("user", password)))

        assertThat(result).doesNotContain(token).doesNotContain(htmlEscaped).doesNotContain("p<w>").doesNotContain("p\\u003cw")
        val parsed = com.fasterxml.jackson.databind.ObjectMapper().readTree(result)
        val alert = parsed.path("site")[0].path("alerts")[0]
        assertThat(alert.path("name").asText()).isEqualTo("n")
        assertThat(alert.path("otherinfo").asText()).doesNotContain(htmlEscaped).contains("******")
        val instance = alert.path("instances")[0]
        assertThat(instance.path("uri").asText()).isEqualTo("https://dev.example.com/a")
        assertThat(instance.path("evidence").asText()).isEqualTo("Authorization: Basic ******")
        assertThat(instance.path("otherinfo").asText()).doesNotContain(password)
    }

    @Test
    @DisplayName("인증 정보는 docker 명령줄에 나오지 않는다")
    fun credentials_never_reach_the_command_line() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com", credentials))

        assertThat(Files.readString(calls)).doesNotContain("topsecret").doesNotContain(basicToken)
    }

    @Test
    @DisplayName("인증 정보를 넣은 점검 계획도 점검이 끝나면 지운다")
    fun plan_with_credentials_is_removed() {
        scanner("$writeReport; exit 0").scan(target("https://dev.example.com", credentials))

        val workDir = Path.of(Files.readString(tempDir.resolve("workdir.txt")).trim())
        assertThat(Files.exists(workDir.resolve("plan.yaml"))).isFalse()
    }

    @Test
    @DisplayName("점검 계획 파일을 써서 ZAP에 넘기고, 기본 점검 스크립트와 규칙 전체 갱신은 쓰지 않는다")
    fun runs_zap_with_its_own_plan() {
        scanner("$writeReport; exit 0").scan(target("http://localhost:3000"))

        assertThat(Files.readString(calls))
            .contains("zap-image zap.sh -cmd -addoninstall pscanrulesBeta -autorun /zap/wrk/plan.yaml")
            .doesNotContain("zap-baseline.py")
            .doesNotContain("-addonupdate")
        assertThat(Files.readString(planCopy)).contains("type: spider").contains("maxDuration: 1").contains("template: traditional-json")
    }

    @Test
    @DisplayName("점검 계획에서 POST 폼 제출을 끈다")
    fun plan_disables_post_form_submission() {
        scanner("$writeReport; exit 0").scan(target("http://localhost:3000"))

        assertThat(Files.readString(planCopy)).contains("postForm: false")
    }

    @Test
    @DisplayName("localhost는 컨테이너에서 이 컴퓨터를 가리키는 이름으로 바꿔 넘기고, 결과에서는 다시 localhost로 되돌린다")
    fun localhost_is_translated_for_the_container() {
        val result = scanner("$writeReport; exit 0").scan(target("http://localhost:3000"))

        assertThat(Files.readString(planCopy)).contains("'http://host.docker.internal:3000'").doesNotContain("localhost")
        assertThat(result).isEqualTo("""{"target":"http://localhost:3000"}""")
    }

    @Test
    @DisplayName("그 밖의 주소는 그대로 넘긴다")
    fun other_hosts_are_passed_as_is() {
        val result = scanner("$writeReport; exit 0").scan(target("https://dev.example.com"))

        assertThat(Files.readString(planCopy)).contains("'https://dev.example.com'")
        assertThat(result).isEqualTo("""{"target":"https://dev.example.com"}""")
    }

    @Test
    @DisplayName("이름이 localhost로 시작하기만 하는 주소는 바꾸지 않는다")
    fun host_that_only_starts_with_localhost_is_not_translated() {
        val result = scanner("$writeReport; exit 0").scan(target("https://localhost.example.com"))

        assertThat(Files.readString(planCopy)).contains("'https://localhost.example.com'")
        assertThat(result).isEqualTo("""{"target":"https://localhost.example.com"}""")
    }

    @Test
    @DisplayName("127.0.0.1도 이 컴퓨터를 가리키는 이름으로 바꿔 넘기고 결과에서 되돌린다")
    fun loopback_address_is_translated() {
        val result = scanner("$writeReport; exit 0").scan(target("http://127.0.0.1:8080"))

        assertThat(Files.readString(planCopy)).contains("'http://host.docker.internal:8080'")
        assertThat(result).isEqualTo("""{"target":"http://127.0.0.1:8080"}""")
    }

    @Test
    @DisplayName("결과에서는 주소의 호스트만 되돌리고, 경로나 다른 호스트에 든 같은 글자는 그대로 둔다")
    fun only_the_host_is_restored_in_the_result() {
        val report = """{"plain":"http://host.docker.internal:3000/host.docker.internal.js","escaped":"http:\/\/host.docker.internal:3000\/x","bare":"http://host.docker.internal","other":"https://host.docker.internal.example.com/"}"""

        val result = scanner("printf '%s' '$report' > \"\$dir/report.json\"; exit 0").scan(target("http://localhost:3000"))

        assertThat(result).isEqualTo(
            """{"plain":"http://localhost:3000/host.docker.internal.js","escaped":"http:\/\/localhost:3000\/x","bare":"http://localhost","other":"https://host.docker.internal.example.com/"}"""
        )
    }

    @Test
    @DisplayName("ZAP이 0이 아닌 값으로 끝나면 점검 실패다")
    fun non_zero_exit_code_fails() {
        assertThat(messageOf { scanner("$writeReport; exit 1").scan(target("http://localhost:3000")) }).isEqualTo("ZAP 실행에 실패했습니다(종료 코드 1).")
    }

    @Test
    @DisplayName("결과 파일이 없으면 점검 실패다")
    fun missing_report_fails() {
        assertThat(messageOf { scanner("exit 0").scan(target("http://localhost:3000")) }).isEqualTo("ZAP이 결과 파일을 만들지 않았습니다.")
    }

    @Test
    @DisplayName("제한 시간을 넘기면 컨테이너를 지우고 점검 실패로 끝낸다")
    fun timeout_removes_the_container_and_fails() {
        val message = messageOf { scanner("sleep 30", Duration.ofMillis(300)).scan(target("http://localhost:3000")) }

        assertThat(message).isEqualTo("점검이 제한 시간 안에 끝나지 않았습니다.")
        assertThat(Files.readAllLines(calls).last()).startsWith("rm -f secwatch-zap-")
    }

    @Test
    @DisplayName("컨테이너를 지우는 명령이 끝나지 않으면 그 명령도 강제로 끝낸다")
    fun stuck_cleanup_command_is_killed() {
        val pidFile = tempDir.resolve("rm.pid")
        val scanner = scanner("sleep 30", Duration.ofMillis(300), onRemove = "echo \$\$ > \"$pidFile\"; exec sleep 60")

        messageOf { scanner.scan(target("http://localhost:3000")) }

        val pid = Files.readString(pidFile).trim().toLong()
        Awaitility.await().atMost(Duration.ofSeconds(5)).until { !ProcessHandle.of(pid).map { it.isAlive }.orElse(false) }
    }

    @Test
    @DisplayName("점검 도중 스레드가 중단돼도 컨테이너를 지우고 점검 실패로 끝내며 중단 표시를 남긴다")
    fun interrupted_scan_removes_the_container() {
        val scanner = scanner("sleep 30", Duration.ofSeconds(60))
        var failure: Throwable? = null
        var stillInterrupted = false
        val thread = Thread {
            try {
                scanner.scan(target("http://localhost:3000"))
            } catch (e: Throwable) {
                failure = e
            }
            stillInterrupted = Thread.currentThread().isInterrupted
        }
        thread.start()
        Awaitility.await().atMost(Duration.ofSeconds(10)).until { Files.exists(calls) && Files.readString(calls).contains("run ") }

        thread.interrupt()
        thread.join(10_000)

        assertThat(thread.isAlive).isFalse()
        assertThat(failure).isInstanceOf(SiteScanFailedException::class.java).hasMessage("점검이 중단됐습니다.")
        assertThat(stillInterrupted).isTrue()
        Awaitility.await().atMost(Duration.ofSeconds(5)).until { Files.readAllLines(calls).last().startsWith("rm -f secwatch-zap-") }
    }

    @Test
    @DisplayName("Docker를 실행할 수 없으면 점검 실패다")
    fun docker_not_available_fails() {
        val scanner = ZapDockerScanner(tempDir.resolve("no-such-docker").toString(), "zap-image", 1, Duration.ofSeconds(5))

        assertThat(messageOf { scanner.scan(target("http://localhost:3000")) }).isEqualTo("Docker를 실행하지 못했습니다.")
    }

    @Test
    @DisplayName("점검이 끝나면 임시 폴더를 지운다")
    fun work_directory_is_removed() {
        scanner("$writeReport; exit 0").scan(target("http://localhost:3000"))

        val workDir = Path.of(Files.readString(tempDir.resolve("workdir.txt")).trim())
        assertThat(Files.exists(workDir)).isFalse()
        assertThat(Files.exists(workDir.parent)).isFalse()
    }

    @Test
    @DisplayName("결과를 쓰는 폴더는 다른 계정이 들어올 수 없는 폴더 안에 둔다")
    fun work_directory_sits_inside_a_private_directory() {
        scanner("$writeReport; exit 0").scan(target("http://localhost:3000"))

        assertThat(Files.readString(tempDir.resolve("parent-mode.txt")).trim()).isEqualTo("drwx------")
    }

    @Test
    @DisplayName("임시 폴더를 지울 때 그 안의 링크가 가리키는 바깥 파일은 지우지 않는다")
    fun cleanup_does_not_follow_symbolic_links() {
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        val keep = Files.writeString(outside.resolve("keep.txt"), "keep")

        scanner("$writeReport; ln -s \"$outside\" \"\$dir/link\"; exit 0").scan(target("http://localhost:3000"))

        assertThat(Files.exists(keep)).isTrue()
    }
}
