package com.example.secwatch.web

import com.example.secwatch.support.FakeExternalApiIntegrationTest
import java.time.Duration
import org.awaitility.Awaitility
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@DisplayName("사이트 점검 API")
class SiteScanControllerTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    private var projectId = 0L
    private var outsideProjectId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("insert into project(name, domain) values ('local', 'localhost:3000'), ('outside', 'outside.example.com')")
        projectId = jdbcTemplate.queryForObject("select id from project where name = 'local'", Long::class.java)!!
        outsideProjectId = jdbcTemplate.queryForObject("select id from project where name = 'outside'", Long::class.java)!!
    }

    private fun awaitNoRunningScan() = Awaitility.await().atMost(Duration.ofSeconds(15)).until {
        jdbcTemplate.queryForObject("select count(*) from site_scan where status = 'RUNNING'", Int::class.java) == 0
    }

    @Test
    @DisplayName("점검을 요청하면 202와 점검 번호를 돌려주고, 끝나면 기록과 발견 항목을 조회할 수 있다")
    fun request_then_read_scans_and_findings() {
        fakeSiteScanner.result = javaClass.getResource("/fixtures/zap-report-small.json")!!.readText()

        mockMvc.perform(post("/api/projects/$projectId/site-scans"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.data.scanId").isNumber)
        awaitNoRunningScan()

        mockMvc.perform(get("/api/projects/$projectId/site-scans"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data[0].scanType").value("BASELINE"))
        mockMvc.perform(get("/api/projects/$projectId/site-findings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", hasSize<Any>(4)))
            .andExpect(jsonPath("$.data[0].risk").value("MEDIUM"))
            .andExpect(jsonPath("$.data[0].status").value("NEW"))
            .andExpect(jsonPath("$.data[0].references").isArray)
            .andExpect(jsonPath("$.data[0].sampleUrls").isArray)
    }

    @Test
    @DisplayName("허용 목록에 없는 주소의 점검 요청은 403과 안내 문구를 돌려준다")
    fun target_outside_the_allow_list_is_403() {
        mockMvc.perform(post("/api/projects/$outsideProjectId/site-scans"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(40301))
            .andExpect(jsonPath("$.message").value("점검이 허용되지 않은 주소입니다. 설정의 허용 목록에 있는 주소만 점검할 수 있습니다."))
    }

    @Test
    @DisplayName("없는 프로젝트의 점검 요청은 404를 돌려준다")
    fun unknown_project_is_404() {
        mockMvc.perform(post("/api/projects/999999/site-scans")).andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("방금 성공한 프로젝트를 다시 요청하면 429와 안내 문구를 돌려준다")
    fun too_soon_is_429() {
        mockMvc.perform(post("/api/projects/$projectId/site-scans")).andExpect(status().isAccepted)
        awaitNoRunningScan()

        mockMvc.perform(post("/api/projects/$projectId/site-scans"))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value(42901))
    }
}
