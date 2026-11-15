package com.example.secwatch.dependency

import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.DbIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("의존성과 프로젝트의 연결")
class DependencyMappingTest : DbIntegrationTest() {
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository

    @Test
    @DisplayName("의존성에서 속한 프로젝트를 읽을 수 있다")
    fun dependency_knows_its_project() {
        val project = projectRepository.save(Project("모꼬지", "mokkoji.kr"))
        dependencyRepository.save(Dependency(project, "npm", "a", "1.0.0"))

        val projectNames = TransactionTemplate(transactionManager).execute {
            dependencyRepository.findByProjectId(project.id!!).map { dependency -> dependency.project.name }
        }

        assertThat(projectNames).containsExactly("모꼬지")
    }
}
