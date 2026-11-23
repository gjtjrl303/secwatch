package com.example.secwatch.web

import com.example.secwatch.support.FakeSourceConfig
import com.example.secwatch.support.MySqlContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.util.LinkedMultiValueMap

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "secwatch.recheck.cron=-",
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=64KB",
    ],
)
@Import(MySqlContainerConfig::class, FakeSourceConfig::class)
@DisplayName("너무 큰 파일 올리기")
class UploadTooLargeTest {
    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    private fun post(path: String, fields: Map<String, String>, fileBytes: Int) = restTemplate.postForEntity(
        path,
        HttpEntity(
            LinkedMultiValueMap<String, Any>().apply {
                fields.forEach { (name, value) -> add(name, value) }
                add("file", object : ByteArrayResource(ByteArray(fileBytes) { 'a'.code.toByte() }) {
                    override fun getFilename() = "deps.txt"
                })
            },
            HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA },
        ),
        String::class.java,
    )

    @Test
    @DisplayName("다시 가져오기에 한도를 넘는 파일을 올리면 413과 안내 문구가 있는 화면을 보여준다")
    fun too_large_reimport_shows_the_message() {
        jdbcTemplate.update("delete from project")
        jdbcTemplate.update("insert into project(name, domain) values ('t', 'big.example.com')")
        val projectId = jdbcTemplate.queryForObject("select id from project", Long::class.java)

        val response = post("/projects/$projectId/dependencies", emptyMap(), 8 * 1024)

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(response.body).contains("파일이 너무 큽니다.").contains("목록으로")
    }

    @Test
    @DisplayName("등록할 때 한도를 넘는 파일을 올리면 413과 안내 문구를 보여주고 프로젝트를 만들지 않는다")
    fun too_large_create_shows_the_message_and_creates_nothing() {
        jdbcTemplate.update("delete from project")

        val response = post("/projects", mapOf("name" to "big", "domain" to "big2.example.com"), 8 * 1024)

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(response.body).contains("파일이 너무 큽니다.")
        assertThat(jdbcTemplate.queryForObject("select count(*) from project", Int::class.java)).isEqualTo(0)
    }
}
