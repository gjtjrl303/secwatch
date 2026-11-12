package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer
import org.springframework.dao.DataIntegrityViolationException

@DisplayName("프로젝트 등록")
class ProjectRegistrationTest {

    private fun registrationFailingToSave(message: String): ProjectRegistration {
        val projectRepository = mock(ProjectRepository::class.java, Answer { invocation ->
            when (invocation.method.name) {
                "existsByDomain" -> false
                "save" -> throw DataIntegrityViolationException(message)
                else -> null
            }
        })
        return ProjectRegistration(projectRepository)
    }

    @Test
    @DisplayName("도메인 유니크 제약을 어겨 저장이 실패하면 중복 오류로 바꾸고 원인을 보존한다")
    fun domain_unique_violation_becomes_duplicate() {
        val projectRegistration = registrationFailingToSave("Duplicate entry 'a.com' for key 'project.uk_project_domain'")

        val e = assertThrows<SecwatchException> { projectRegistration.register("a", "a.com") }

        assertThat(e.errorCode).isEqualTo(ErrorCode.DUPLICATE_DOMAIN)
        assertThat(e.cause).isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    @DisplayName("다른 이유로 저장이 실패하면 중복 오류로 바꾸지 않고 그대로 던진다")
    fun other_integrity_violation_is_not_duplicate() {
        val projectRegistration = registrationFailingToSave("Data too long for column 'name' at row 1")

        assertThrows<DataIntegrityViolationException> { projectRegistration.register("a", "a.com") }
    }
}
