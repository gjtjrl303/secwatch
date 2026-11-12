package com.example.secwatch.dependency

import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.DbIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("의존성 등록 동시성")
class DependencyImporterConcurrencyTest : DbIntegrationTest() {
    @Autowired lateinit var importer: DependencyImporter
    @Autowired lateinit var projects: ProjectRepository

    private val lock = """{"lockfileVersion":3,"packages":{"":{},"node_modules/a":{"version":"1.0.0"}}}"""

    @Test
    @DisplayName("다른 등록이 커밋되는 중에 같은 항목을 등록해도 중복 오류 없이 끝난다")
    fun import_started_during_another_commit_does_not_fail_on_the_duplicate_row() {
        val projectId = projects.save(Project("s", "s.example.com")).id!!

        // 이중 제출: 첫 가져오기가 커밋 중이고 a@1.0.0을 이미 넣었다
        val result = blockedWhileProjectIsLocked(
            projectId,
            uncommitted = {
                jdbc.update(
                    "insert into dependency (project_id, ecosystem, name, version) values (?, 'npm', 'a', '1.0.0')", projectId
                )
            },
        ) { importer.import(projectId, lock) }

        assertThat(result.parsed).isEqualTo(1)
        assertThat(count("dependency")).isEqualTo(1)
    }
}
