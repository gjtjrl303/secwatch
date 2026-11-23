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

    /** docker 대신 부를 스크립트다. 받은 인자를 적어 두고, 마운트한 폴더에 [body]가 시키는 대로 결과를 쓴다. */
    private fun fakeDocker(body: String): String {
        val script = tempDir.resolve("docker.sh")
        Files.writeString(
            script,
            """
            #!/bin/sh
            echo "${'$'}@" >> "$calls"
            if [ "${'$'}1" = "rm" ]; then exit 0; fi
            dir=""; url=""
            while [ ${'$'}# -gt 0 ]; do
              if [ "${'$'}1" = "-v" ]; then dir="${'$'}{2%%:*}"; fi
              if [ "${'$'}1" = "-t" ]; then url="${'$'}2"; fi
              shift
            done
            echo "${'$'}dir" > "${tempDir.resolve("workdir.txt")}"
            $body
            """.trimIndent(),
        )
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
        return script.toString()
    }

    private fun scanner(body: String, timeout: Duration = Duration.ofSeconds(10)) =
        ZapDockerScanner(fakeDocker(body), "zap-image", 1, timeout)

    private val writeReport = """printf '{"target":"%s"}' "${'$'}url" > "${'$'}dir/report.json""""

    private fun messageOf(action: () -> Unit) = assertThrows<SiteScanFailedException> { action() }.message

    @Test
    @DisplayName("localhost는 컨테이너에서 이 컴퓨터를 가리키는 이름으로 바꿔 넘기고, 결과에서는 다시 localhost로 되돌린다")
    fun localhost_is_translated_for_the_container() {
        val result = scanner("$writeReport; exit 0").scan("http://localhost:3000")

        assertThat(Files.readString(calls)).contains("-t http://host.docker.internal:3000").contains("zap-baseline.py").contains("-m 1")
        assertThat(result).isEqualTo("""{"target":"http://localhost:3000"}""")
    }

    @Test
    @DisplayName("그 밖의 주소는 그대로 넘긴다")
    fun other_hosts_are_passed_as_is() {
        val result = scanner("$writeReport; exit 0").scan("https://dev.example.com")

        assertThat(Files.readString(calls)).contains("-t https://dev.example.com")
        assertThat(result).isEqualTo("""{"target":"https://dev.example.com"}""")
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
    @DisplayName("경고 항목이 있어서 1이나 2로 끝나도 결과를 읽는다")
    fun warning_exit_codes_are_success() {
        assertThat(scanner("$writeReport; exit 2").scan("http://localhost:3000")).contains("localhost")
        assertThat(scanner("$writeReport; exit 1").scan("http://localhost:3000")).contains("localhost")
    }

    @Test
    @DisplayName("그 밖의 종료 코드는 점검 실패다")
    fun other_exit_codes_fail() {
        assertThat(messageOf { scanner("$writeReport; exit 3").scan("http://localhost:3000") }).isEqualTo("ZAP 실행에 실패했습니다(종료 코드 3).")
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
