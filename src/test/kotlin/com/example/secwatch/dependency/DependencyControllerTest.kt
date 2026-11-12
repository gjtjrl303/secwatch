package com.example.secwatch.dependency

import com.example.secwatch.support.DbIntegrationTest
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@RecordApplicationEvents
@DisplayName("의존성 등록 API")
class DependencyControllerTest : DbIntegrationTest() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var events: ApplicationEvents

    private var serviceId = 0L

    @BeforeEach
    fun setUp() {
        jdbc.update("insert into monitored_service(name, domain) values ('t', 'juice.example.com')")
        serviceId = jdbc.queryForObject("select id from monitored_service", Long::class.java)!!
    }

    private fun lock(vararg libs: Pair<String, String>, extra: String = ""): String {
        val entries = libs.joinToString(",") { """"node_modules/${it.first}":{"version":"${it.second}"}""" }
        return """{"lockfileVersion":3,"packages":{"":{}${if (entries.isEmpty()) "" else ",$entries"}$extra}}"""
    }

    private fun import(id: Long, content: String) = mvc.perform(
        post("/api/services/$id/dependencies")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("content" to content)))
    )

    private fun rows() = jdbc.queryForList(
        "select id, name, version from dependency where service_id = ? order by name", serviceId
    )

    @Test
    @DisplayName("내용이 비어 있으면 400과 안내 문구를 돌려준다")
    fun blank_content_is_400() {
        import(serviceId, "   ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("의존성 목록을 붙여넣어 주세요."))
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 400과 안내 문구를 돌려준다")
    fun unsupported_is_400() {
        import(serviceId, "not json")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("지원하지 않는 형식입니다. package-lock.json 내용을 붙여넣어 주세요."))
    }

    @Test
    @DisplayName("없는 서비스에 등록하면 404와 안내 문구를 돌려준다")
    fun unknown_service_is_404() {
        import(999999, lock("a" to "1.0.0"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("서비스를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("등록하면 202와 읽은 개수를 돌려주고 의존성을 저장한다")
    fun import_returns_202_with_counts_and_stores_rows() {
        import(serviceId, lock("a" to "1.0.0", "b" to "2.0.0"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.parsed").value(2))
            .andExpect(jsonPath("$.skipped").value(0))
        assertThat(rows().map { it["name"] }).containsExactly("a", "b")
    }

    @Test
    @DisplayName("읽지 못해 건너뛴 항목 수를 알려준다")
    fun skipped_reported() {
        val withLink = lock("a" to "1.0.0", extra = ""","node_modules/l":{"link":true}""")
        import(serviceId, withLink)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.parsed").value(1))
            .andExpect(jsonPath("$.skipped").value(1))
    }

    @Test
    @DisplayName("다시 등록하면 같은 항목은 유지하고 빠진 것은 지우고 새 것은 추가한다")
    fun import_replaces_by_diff() {
        import(serviceId, lock("a" to "1.0.0", "b" to "1.0.0")).andExpect(status().isAccepted)
        val before = rows().associate { it["name"] as String to it["id"] as Long }

        import(serviceId, lock("b" to "1.0.0", "c" to "1.0.0")).andExpect(status().isAccepted)
        val after = rows().associate { it["name"] as String to it["id"] as Long }

        assertThat(after.keys).containsExactlyInAnyOrder("b", "c")
        assertThat(after["b"]).isEqualTo(before["b"])
    }

    @Test
    @DisplayName("등록하면 해당 서비스의 의존성 가져옴 이벤트가 발행된다")
    fun event_published_for_the_service() {
        import(serviceId, lock("a" to "1.0.0")).andExpect(status().isAccepted)

        val published = events.stream(DependenciesImported::class.java).toList()

        assertThat(published).containsExactly(DependenciesImported(serviceId))
    }
}
