package com.example.secwatch.sitescan

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
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
                "cp \"\$dir/plan.yaml\" \"$planCopy\"",
                "url=\$(grep -m1 'url:' \"\$dir/plan.yaml\" | sed \"s/.*url: *//; s/'//g\")",
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

    @Test
    @DisplayName("점검 계획 파일을 써서 ZAP에 넘기고, 기본 점검 스크립트와 규칙 전체 갱신은 쓰지 않는다")
    fun runs_zap_with_its_own_plan() {
        scanner("$writeReport; exit 0").scan("http://localhost:3000")

        assertThat(Files.readString(calls))
            .contains("zap-image zap.sh -cmd -addoninstall pscanrulesBeta -autorun /zap/wrk/plan.yaml")
            .doesNotContain("zap-baseline.py")
            .doesNotContain("-addonupdate")
        assertThat(Files.readString(planCopy)).contains("type: spider").contains("maxDuration: 1").contains("template: traditional-json")
    }

    @Test
    @DisplayName("점검 계획에서 POST 폼 제출을 끈다")
    fun plan_disables_post_form_submission() {
        scanner("$writeReport; exit 0").scan("http://localhost:3000")

        assertThat(Files.readString(planCopy)).contains("postForm: false")
    }

    @Test
    @DisplayName("localhost는 컨테이너에서 이 컴퓨터를 가리키는 이름으로 바꿔 넘기고, 결과에서는 다시 localhost로 되돌린다")
    fun localhost_is_translated_for_the_container() {
        val result = scanner("$writeReport; exit 0").scan("http://localhost:3000")

        assertThat(Files.readString(planCopy)).contains("'http://host.docker.internal:3000'").doesNotContain("localhost")
        assertThat(result).isEqualTo("""{"target":"http://localhost:3000"}""")
    }

    @Test
    @DisplayName("그 밖의 주소는 그대로 넘긴다")
    fun other_hosts_are_passed_as_is() {
        val result = scanner("$writeReport; exit 0").scan("https://dev.example.com")

        assertThat(Files.readString(planCopy)).contains("'https://dev.example.com'")
        assertThat(result).isEqualTo("""{"target":"https://dev.example.com"}""")
    }

    @Test
    @DisplayName("이름이 localhost로 시작하기만 하는 주소는 바꾸지 않는다")
    fun host_that_only_starts_with_localhost_is_not_translated() {
        val result = scanner("$writeReport; exit 0").scan("https://localhost.example.com")

        assertThat(Files.readString(planCopy)).contains("'https://localhost.example.com'")
        assertThat(result).isEqualTo("""{"target":"https://localhost.example.com"}""")
    }

    @Test
    @DisplayName("127.0.0.1도 이 컴퓨터를 가리키는 이름으로 바꿔 넘기고 결과에서 되돌린다")
    fun loopback_address_is_translated() {
        val result = scanner("$writeReport; exit 0").scan("http://127.0.0.1:8080")

        assertThat(Files.readString(planCopy)).contains("'http://host.docker.internal:8080'")
        assertThat(result).isEqualTo("""{"target":"http://127.0.0.1:8080"}""")
    }

    @Test
    @DisplayName("결과에서는 주소의 호스트만 되돌리고, 경로나 다른 호스트에 든 같은 글자는 그대로 둔다")
    fun only_the_host_is_restored_in_the_result() {
        val report = """{"plain":"http://host.docker.internal:3000/host.docker.internal.js","escaped":"http:\/\/host.docker.internal:3000\/x","bare":"http://host.docker.internal","other":"https://host.docker.internal.example.com/"}"""

        val result = scanner("printf '%s' '$report' > \"\$dir/report.json\"; exit 0").scan("http://localhost:3000")

        assertThat(result).isEqualTo(
            """{"plain":"http://localhost:3000/host.docker.internal.js","escaped":"http:\/\/localhost:3000\/x","bare":"http://localhost","other":"https://host.docker.internal.example.com/"}"""
        )
    }

    @Test
    @DisplayName("ZAP이 0이 아닌 값으로 끝나면 점검 실패다")
    fun non_zero_exit_code_fails() {
        assertThat(messageOf { scanner("$writeReport; exit 1").scan("http://localhost:3000") }).isEqualTo("ZAP 실행에 실패했습니다(종료 코드 1).")
    }

    @Test
    @DisplayName("결과 파일이 없으면 점검 실패다")
    fun missing_report_fails() {
        assertThat(messageOf { scanner("exit 0").scan("http://localhost:3000") }).isEqualTo("ZAP이 결과 파일을 만들지 않았습니다.")
    }

    @Test
    @DisplayName("제한 시간을 넘기면 컨테이너를 지우고 점검 실패로 끝낸다")
    fun timeout_removes_the_container_and_fails() {
        val message = messageOf { scanner("sleep 30", Duration.ofMillis(300)).scan("http://localhost:3000") }

        assertThat(message).isEqualTo("점검이 제한 시간 안에 끝나지 않았습니다.")
        assertThat(Files.readAllLines(calls).last()).startsWith("rm -f secwatch-zap-")
    }

    @Test
    @DisplayName("컨테이너를 지우는 명령이 끝나지 않으면 그 명령도 강제로 끝낸다")
    fun stuck_cleanup_command_is_killed() {
        val pidFile = tempDir.resolve("rm.pid")
        val scanner = scanner("sleep 30", Duration.ofMillis(300), onRemove = "echo \$\$ > \"$pidFile\"; exec sleep 60")

        messageOf { scanner.scan("http://localhost:3000") }

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
                scanner.scan("http://localhost:3000")
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

        assertThat(messageOf { scanner.scan("http://localhost:3000") }).isEqualTo("Docker를 실행하지 못했습니다.")
    }

    @Test
    @DisplayName("점검이 끝나면 임시 폴더를 지운다")
    fun work_directory_is_removed() {
        scanner("$writeReport; exit 0").scan("http://localhost:3000")

        val workDir = Path.of(Files.readString(tempDir.resolve("workdir.txt")).trim())
        assertThat(Files.exists(workDir)).isFalse()
    }
}
