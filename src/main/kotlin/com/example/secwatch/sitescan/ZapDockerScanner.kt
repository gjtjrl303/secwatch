package com.example.secwatch.sitescan

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Docker로 ZAP을 띄워 사이트를 둘러보고 응답만 살펴본다. 공격 요청을 보내지 않고 POST 폼도 제출하지 않는다. */
@Component
class ZapDockerScanner(
    @Value("\${secwatch.site-scan.docker-command:docker}") private val dockerCommand: String,
    @Value("\${secwatch.site-scan.zap-image:ghcr.io/zaproxy/zaproxy:stable}") private val zapImage: String,
    @Value("\${secwatch.site-scan.spider-minutes:1}") private val spiderMinutes: Int,
    @Value("\${secwatch.site-scan.timeout:5m}") private val timeout: Duration,
) : SiteScanner {

    override fun scan(targetUrl: String): String {
        val localHost = localHostOf(targetUrl)
        // 컨테이너 안에서 localhost는 컨테이너 자신이라서, 이 컴퓨터를 가리키는 이름으로 바꿔 넘긴다
        val containerUrl = if (localHost == null) targetUrl else targetUrl.replaceFirst("://$localHost", "://$HOST_FROM_CONTAINER")
        val containerName = "secwatch-zap-" + UUID.randomUUID().toString().take(8)
        val workDir = Files.createTempDirectory("secwatch-zap")
        try {
            // ZAP은 컨테이너 안의 다른 사용자로 돌아서, 결과를 쓸 수 있게 열어 둔다
            Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString("rwxrwxrwx"))
            Files.writeString(workDir.resolve(PLAN_FILE), plan(containerUrl))
            run(command(containerName, workDir), containerName, workDir.resolve("zap.log"))

            val report = workDir.resolve(REPORT_FILE)
            if (!Files.exists(report)) throw SiteScanFailedException("ZAP이 결과 파일을 만들지 않았습니다.")
            val rawJson = Files.readString(report)
            return if (localHost == null) rawJson else rawJson.replace(CONTAINER_HOST_IN_URL) { match -> match.groupValues[1] + localHost }
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    /**
     * ZAP의 기본 점검 스크립트(zap-baseline.py)와 같은 순서와 같은 규칙이지만 두 가지가 다르다.
     * 스크립트는 페이지의 POST 폼을 제출하고, 시작할 때마다 설치된 규칙 전체를 갱신한다.
     */
    private fun plan(containerUrl: String) = """
        env:
          contexts:
          - name: secwatch
            urls:
            - '$containerUrl'
          parameters:
            failOnError: true
            progressToStdout: false
        jobs:
        - type: passiveScan-config
          parameters:
            enableTags: false
            maxAlertsPerRule: 10
        - type: spider
          parameters:
            url: '$containerUrl'
            maxDuration: $spiderMinutes
            postForm: false
        - type: passiveScan-wait
          parameters:
            maxDuration: 0
        - type: report
          parameters:
            template: traditional-json
            reportDir: /zap/wrk/
            reportFile: $REPORT_FILE
    """.trimIndent() + "\n"

    private fun command(containerName: String, workDir: Path) = listOf(
        dockerCommand, "run", "--rm", "--name", containerName,
        "--add-host=$HOST_FROM_CONTAINER:host-gateway",
        "-v", "$workDir:/zap/wrk:rw",
        zapImage, "zap.sh", "-cmd", "-addoninstall", BETA_PASSIVE_RULES, "-autorun", "/zap/wrk/$PLAN_FILE",
    )

    private fun run(command: List<String>, containerName: String, logFile: Path) {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile()).start()
        } catch (e: IOException) {
            throw SiteScanFailedException("Docker를 실행하지 못했습니다.", e)
        }
        var interrupted = false
        val finished = try {
            process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            interrupted = true
            false
        }
        if (!finished) {
            process.destroyForcibly()
            removeContainer(containerName)
        }
        if (interrupted) {
            // 컨테이너를 지운 뒤에 중단 표시를 되돌린다. 먼저 되돌리면 지우는 명령을 기다리지 못한다.
            Thread.currentThread().interrupt()
            throw SiteScanFailedException("점검이 중단됐습니다.")
        }
        if (!finished) throw SiteScanFailedException("점검이 제한 시간 안에 끝나지 않았습니다.")
        if (process.exitValue() != 0) throw SiteScanFailedException("ZAP 실행에 실패했습니다(종료 코드 ${process.exitValue()}).")
    }

    private fun removeContainer(containerName: String) {
        try {
            ProcessBuilder(dockerCommand, "rm", "-f", containerName).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(CLEANUP_SECONDS, TimeUnit.SECONDS)
        } catch (e: IOException) {
            // 컨테이너는 --rm으로 띄워서 끝나면 스스로 지워진다
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun localHostOf(targetUrl: String): String? =
        targetUrl.substringAfter("://").substringBefore('/').substringBefore(':').takeIf { host -> host in LOCAL_HOSTS }

    private companion object {
        const val HOST_FROM_CONTAINER = "host.docker.internal"
        const val PLAN_FILE = "plan.yaml"
        const val REPORT_FILE = "report.json"
        const val CLEANUP_SECONDS = 10L

        // 기본 점검 스크립트가 함께 쓰는 규칙 묶음이다. 빼면 연습용 사이트에서 10종이던 항목이 3종으로 준다.
        const val BETA_PASSIVE_RULES = "pscanrulesBeta"
        val LOCAL_HOSTS = listOf("localhost", "127.0.0.1")

        // 주소의 호스트 자리만 고른다. ZAP 결과에는 `://`와 JSON이 빗금을 이스케이프한 `:\/\/`가 섞여 있다.
        val CONTAINER_HOST_IN_URL = Regex("""(:(?://|\\/\\/))host\.docker\.internal(?=[:/"\\?#]|$)""")
    }
}
