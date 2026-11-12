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
@DisplayName("프로젝트 등록 API")
class ProjectControllerTest : MySqlIntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clean() {
        jdbc.update("delete from project")
    }

    private fun register(name: String, domain: String) =
        mvc.perform(
            post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","domain":"$domain"}""")
        )

    @Test
    @DisplayName("등록하면 201과 프로젝트 정보를 돌려준다")
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
            .andExpect(jsonPath("$.code").value(40001))
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
            .andExpect(jsonPath("$.code").value(40901))
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
    @DisplayName("목록 조회가 등록된 프로젝트를 돌려준다")
    fun list_returns_registered_projects() {
        register("a", "a.example.com").andExpect(status().isCreated)
        mvc.perform(get("/api/projects"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].domain").value("a.example.com"))
    }

    @Test
    @DisplayName("없는 프로젝트를 조회하면 404와 안내 문구를 돌려준다")
    fun get_unknown_project_is_404() {
        mvc.perform(get("/api/projects/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(40401))
            .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("JSON이 깨진 요청은 400과 공통 오류 형식을 돌려준다")
    fun malformed_json_is_400() {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{broken"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40000))
            .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
    }

    @Test
    @DisplayName("필수 필드가 빠진 요청은 400과 공통 오류 형식을 돌려준다")
    fun missing_field_is_400() {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("""{"name":"x"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40000))
    }

    @Test
    @DisplayName("id가 숫자가 아니면 400과 공통 오류 형식을 돌려준다")
    fun non_numeric_id_is_400() {
        mvc.perform(get("/api/projects/abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40000))
    }

    @Test
    @DisplayName("이름이 비어 있으면 400을 돌려준다")
    fun blank_name_is_400() {
        register("   ", "blank.example.com")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40007))
    }

    @Test
    @DisplayName("이름이 100자를 넘으면 400을 돌려주고 100자는 받아들인다")
    fun name_length_limit() {
        register("가".repeat(101), "long-name.example.com")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40007))
        register("가".repeat(100), "max-name.example.com").andExpect(status().isCreated)
    }

    @Test
    @DisplayName("도메인이 255자를 넘으면 400을 돌려주고 255자는 받아들인다")
    fun domain_length_limit() {
        register("x", "a".repeat(254) + ".kr")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(40001))
        register("x", "a".repeat(252) + ".kr").andExpect(status().isCreated)
    }
}
