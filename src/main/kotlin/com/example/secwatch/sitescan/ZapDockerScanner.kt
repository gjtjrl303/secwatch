package com.example.secwatch.sitescan

import com.example.secwatch.project.SiteCredentials
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

/** Docker로 ZAP을 띄워 사이트를 둘러보고 응답만 살펴본다. 공격 요청을 보내지 않고 POST 폼도 제출하지 않는다. */
@Component
class ZapDockerScanner(
    @Value("\${secwatch.site-scan.docker-command:docker}") private val dockerCommand: String,
    @Value("\${secwatch.site-scan.zap-image:ghcr.io/zaproxy/zaproxy:stable}") private val zapImage: String,
    @Value("\${secwatch.site-scan.spider-minutes:1}") private val spiderMinutes: Int,
    @Value("\${secwatch.site-scan.timeout:5m}") private val timeout: Duration,
) : SiteScanner {

    override fun scan(target: SiteScanTarget): String {
        val targetUrl = target.url
        val localHost = localHostOf(targetUrl)
        // 컨테이너 안에서 localhost는 컨테이너 자신이라서, 이 컴퓨터를 가리키는 이름으로 바꿔 넘긴다
        val containerUrl = if (localHost == null) targetUrl else targetUrl.replaceFirst("://$localHost", "://$HOST_FROM_CONTAINER")
        val containerName = "secwatch-zap-" + UUID.randomUUID().toString().take(8)
        // 바깥 폴더는 이 계정만 들어올 수 있다. 그 안의 폴더만 컨테이너의 다른 사용자가 쓸 수 있게 연다.
        val privateDir = Files.createTempDirectory("secwatch-zap")
        try {
            val workDir = Files.createDirectory(privateDir.resolve("wrk"))
            Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString("rwxrwxrwx"))
            Files.writeString(workDir.resolve(PLAN_FILE), plan(containerUrl, target.credentials))
            run(command(containerName, workDir), containerName, privateDir.resolve("zap.log"))

            val report = workDir.resolve(REPORT_FILE)
            if (!Files.isRegularFile(report)) throw SiteScanFailedException("ZAP이 결과 파일을 만들지 않았습니다.")
            val rawJson = Files.readString(report)
            val restored = if (localHost == null) rawJson else rawJson.replace(CONTAINER_HOST_IN_URL) { match -> match.groupValues[1] + localHost }
            return target.credentials?.let { credentials -> maskCredentials(restored, credentials) } ?: restored
        } finally {
            deleteWithoutFollowingLinks(privateDir)
        }
    }

    /**
     * ZAP의 기본 점검 스크립트(zap-baseline.py)와 같은 순서와 같은 규칙이지만 두 가지가 다르다.
     * 스크립트는 페이지의 POST 폼을 제출하고, 시작할 때마다 설치된 규칙 전체를 갱신한다.
     */
    private fun plan(startUrl: String, credentials: SiteCredentials?): String {
        val origin = originOf(startUrl)
        return """
            |env:
            |  contexts:
            |  - name: secwatch
            |    urls:
            |    - ${quoted(origin)}
            |  parameters:
            |    failOnError: true
            |    progressToStdout: false
            |jobs:
            |${credentials?.let { basicAuthJob(origin, it) }.orEmpty()}- type: passiveScan-config
            |  parameters:
            |    enableTags: false
            |    maxAlertsPerRule: 10
            |${if (credentials != null) CAPTURED_CREDENTIALS_RULE_OFF else ""}- type: spider
            |  parameters:
            |    url: ${quoted(startUrl)}
            |    maxDuration: $spiderMinutes
            |    postForm: false
            |- type: passiveScan-wait
            |  parameters:
            |    maxDuration: 0
            |- type: report
            |  parameters:
            |    template: traditional-json
            |    reportDir: /zap/wrk/
            |    reportFile: $REPORT_FILE
        """.trimMargin() + "\n"
    }

    /** 이 사이트로 가는 요청에만 Authorization 헤더를 붙인다. 다른 사이트로 가는 요청에는 붙이지 않는다. */
    private fun basicAuthJob(origin: String, credentials: SiteCredentials): String {
        val token = Base64.getEncoder().encodeToString("${credentials.username}:${credentials.password}".toByteArray(Charsets.UTF_8))
        return listOf(
            "- type: replacer",
            "  parameters:",
            "    deleteAllRules: true",
            "  rules:",
            "  - description: basic-auth",
            "    url: ${quoted("\\Q$origin\\E(?:[/?#].*)?")}",
            "    matchType: req_header",
            "    matchString: Authorization",
            "    matchRegex: false",
            "    replacementString: ${quoted("Basic $token")}",
        ).joinToString("\n", postfix = "\n")
    }

    /** 결과를 DB에 저장하기 전에, 어느 규칙이 요청 내용을 옮겨 적었더라도 인증 정보가 남지 않게 값마다 가린다. */
    private fun maskCredentials(rawJson: String, credentials: SiteCredentials): String {
        val token = Base64.getEncoder().encodeToString("${credentials.username}:${credentials.password}".toByteArray(Charsets.UTF_8))
        val secrets = listOf(token, "${credentials.username}:${credentials.password}", HtmlUtils.htmlEscape(credentials.password), credentials.password)
            .distinct().sortedByDescending { secret -> secret.length }
        val root = try {
            REPORT_MAPPER.readTree(rawJson)
        } catch (e: JsonProcessingException) {
            throw SiteScanFailedException("ZAP 결과를 읽지 못했습니다.", e)
        }
        maskTextValues(root, secrets)
        return REPORT_MAPPER.writeValueAsString(root)
    }

    private fun maskTextValues(node: JsonNode, secrets: List<String>) {
        fun masked(text: String) = secrets.fold(text) { current, secret -> current.replace(secret, MASK) }
        when (node) {
            is ObjectNode -> node.fieldNames().asSequence().toList().forEach { name ->
                val child = node.get(name)
                if (child.isTextual) node.put(name, masked(child.asText())) else maskTextValues(child, secrets)
            }
            is ArrayNode -> (0 until node.size()).forEach { index ->
                val child = node.get(index)
                if (child.isTextual) node.set(index, TextNode(masked(child.asText()))) else maskTextValues(child, secrets)
            }
        }
    }

    private fun originOf(url: String): String {
        val scheme = url.substringBefore("://")
        val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        return "$scheme://$authority"
    }

    private fun quoted(value: String) = "'" + value.replace("'", "''") + "'"

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
        val removal = try {
            ProcessBuilder(dockerCommand, "rm", "-f", containerName).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: IOException) {
            // 컨테이너는 --rm으로 띄워서 끝나면 스스로 지워진다
            return
        }
        try {
            removal.waitFor(CLEANUP_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            if (removal.isAlive) removal.destroyForcibly()
        }
    }

    /** 컨테이너가 폴더 안에 링크를 만들 수 있어서, 링크를 따라가지 않고 링크 자체만 지운다. */
    private fun deleteWithoutFollowingLinks(directory: Path) {
        try {
            Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(visited: Path, exception: IOException?): FileVisitResult {
                    Files.deleteIfExists(visited)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            // 지우지 못한 임시 파일은 운영체제가 정리한다. 점검 결과에는 영향이 없다.
        }
    }

    private fun localHostOf(targetUrl: String): String? =
        targetUrl.substringAfter("://").substringBefore('/').substringBefore(':').takeIf { host -> host in LOCAL_HOSTS }

    private companion object {
        const val HOST_FROM_CONTAINER = "host.docker.internal"
        const val PLAN_FILE = "plan.yaml"
        const val REPORT_FILE = "report.json"
        const val CLEANUP_SECONDS = 10L
        const val MASK = "******"

        // 이 규칙은 Basic 인증 헤더를 보면 아이디와 비밀번호를 풀어서 결과에 적는다. 헤더는 우리가 붙인 것이라 끈다.
        // YAML에서 따옴표 없는 off는 false로 읽혀서 따옴표를 붙인다.
        const val CAPTURED_CREDENTIALS_RULE_OFF = "  rules:\n  - id: 10105\n    threshold: \"off\"\n"
        val REPORT_MAPPER = ObjectMapper()

        // 기본 점검 스크립트가 함께 쓰는 규칙 묶음이다. 빼면 연습용 사이트에서 10종이던 항목이 3종으로 준다.
        const val BETA_PASSIVE_RULES = "pscanrulesBeta"
        val LOCAL_HOSTS = listOf("localhost", "127.0.0.1")

        // 주소의 호스트 자리만 고른다. ZAP 결과에는 `://`와 JSON이 빗금을 이스케이프한 `:\/\/`가 섞여 있다.
        val CONTAINER_HOST_IN_URL = Regex("""(:(?://|\\/\\/))host\.docker\.internal(?=[:/"\\?#]|$)""")
    }
}
