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

/** Docker로 ZAP 기본 점검(둘러보고 응답만 살펴보는 방식)을 실행한다. */
@Component
class ZapDockerScanner(
    @Value("\${secwatch.site-scan.docker-command:docker}") private val dockerCommand: String,
    @Value("\${secwatch.site-scan.zap-image:ghcr.io/zaproxy/zaproxy:stable}") private val zapImage: String,
    @Value("\${secwatch.site-scan.spider-minutes:1}") private val spiderMinutes: Int,
    @Value("\${secwatch.site-scan.timeout:5m}") private val timeout: Duration,
) : SiteScanner {

    override fun scan(targetUrl: String): String {
        // 컨테이너 안에서 localhost는 컨테이너 자신이라서, 이 컴퓨터를 가리키는 이름으로 바꿔 넘긴다
        val containerUrl = LOCAL_HOSTS.fold(targetUrl) { url, host -> url.replace("://$host", "://$HOST_FROM_CONTAINER") }
        val containerName = "secwatch-zap-" + UUID.randomUUID().toString().take(8)
        val workDir = Files.createTempDirectory("secwatch-zap")
        try {
            // ZAP은 컨테이너 안의 다른 사용자로 돌아서, 결과를 쓸 수 있게 열어 둔다
            Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString("rwxrwxrwx"))
            run(command(containerName, workDir, containerUrl), containerName, workDir)

            val report = workDir.resolve(REPORT_FILE)
            if (!Files.exists(report)) throw SiteScanFailedException("ZAP이 결과 파일을 만들지 않았습니다.")
            return Files.readString(report).replace(HOST_FROM_CONTAINER, hostOf(targetUrl, containerUrl))
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    private fun command(containerName: String, workDir: Path, containerUrl: String) = listOf(
        dockerCommand, "run", "--rm", "--name", containerName,
        "--add-host=$HOST_FROM_CONTAINER:host-gateway",
        "-v", "$workDir:/zap/wrk:rw",
        zapImage, "zap-baseline.py", "-t", containerUrl, "-J", REPORT_FILE, "-m", spiderMinutes.toString(),
    )

    private fun run(command: List<String>, containerName: String, workDir: Path) {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).redirectOutput(workDir.resolve("zap.log").toFile()).start()
        } catch (e: IOException) {
            throw SiteScanFailedException("Docker를 실행하지 못했습니다.", e)
        }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            removeContainer(containerName)
            throw SiteScanFailedException("점검이 제한 시간 안에 끝나지 않았습니다.")
        }
        // ZAP 기본 점검은 경고나 실패 항목이 있으면 1이나 2로 끝난다. 그 밖의 값은 점검 자체가 안 된 것이다.
        if (process.exitValue() !in FINISHED_EXIT_CODES) {
            throw SiteScanFailedException("ZAP 실행에 실패했습니다(종료 코드 ${process.exitValue()}).")
        }
    }

    private fun removeContainer(containerName: String) {
        try {
            ProcessBuilder(dockerCommand, "rm", "-f", containerName).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(10, TimeUnit.SECONDS)
        } catch (e: IOException) {
            // 컨테이너는 --rm으로 띄워서 끝나면 스스로 지워진다
        }
    }

    private fun hostOf(targetUrl: String, containerUrl: String): String =
        if (targetUrl == containerUrl) HOST_FROM_CONTAINER else targetUrl.substringAfter("://").substringBefore(':').substringBefore('/')

    private companion object {
        const val HOST_FROM_CONTAINER = "host.docker.internal"
        const val REPORT_FILE = "report.json"
        val LOCAL_HOSTS = listOf("localhost", "127.0.0.1")
        val FINISHED_EXIT_CODES = setOf(0, 1, 2)
    }
}
