package com.example.secwatch.web

import com.example.secwatch.support.MySqlIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@DisplayName("서비스 등록 API")
class ServiceControllerTest : MySqlIntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clean() {
        jdbc.update("delete from monitored_service")
    }

    private fun register(name: String, domain: String) =
        mvc.perform(
            post("/api/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","domain":"$domain"}""")
        )

    @Test
    @DisplayName("등록하면 201과 서비스 정보를 돌려준다")
    fun register_returns_201() {
        register("모꼬지", "mokkoji.kr")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.name").value("모꼬지"))
            .andExpect(jsonPath("$.domain").value("mokkoji.kr"))
    }

    @Test
    @DisplayName("도메인은 소문자로 바꿔 저장한다")
    fun domain_is_lowercased() {
        register("x", "Mokkoji.KR")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.domain").value("mokkoji.kr"))
    }

    @Test
    @DisplayName("http:// 같은 스킴이 붙은 도메인은 400을 돌려준다")
    fun domain_with_scheme_is_400() {
        register("x", "https://mokkoji.kr")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("올바른 도메인을 입력해주세요."))
    }

    @Test
    @DisplayName("공백이 들어간 도메인은 400을 돌려준다")
    fun domain_with_space_is_400() {
        register("x", "mokkoji kr")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("올바른 도메인을 입력해주세요."))
    }

    @Test
    @DisplayName("이미 등록된 도메인은 409를 돌려준다")
    fun duplicate_domain_is_409() {
        register("a", "mokkoji.kr").andExpect(status().isCreated)
        register("b", "mokkoji.kr")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("이미 등록된 도메인입니다."))
    }

    @Test
    @DisplayName("localhost:3000 같은 포트가 붙은 주소를 받아들인다")
    fun localhost_with_port_is_accepted() {
        register("juice", "localhost:3000")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.domain").value("localhost:3000"))
    }

    @Test
    @DisplayName("목록 조회가 등록된 서비스를 돌려준다")
    fun list_returns_registered_services() {
        register("a", "a.example.com").andExpect(status().isCreated)
        mvc.perform(get("/api/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].domain").value("a.example.com"))
    }

    @Test
    @DisplayName("없는 서비스를 조회하면 404와 안내 문구를 돌려준다")
    fun get_unknown_service_is_404() {
        mvc.perform(get("/api/services/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("서비스를 찾을 수 없습니다."))
    }
}
