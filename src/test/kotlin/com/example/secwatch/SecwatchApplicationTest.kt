package com.example.secwatch

import com.example.secwatch.support.MySqlIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("앱 시작")
class SecwatchApplicationTest : MySqlIntegrationTest() {
    @Test
    @DisplayName("MySQL에 연결되어 앱이 뜬다")
    fun context_loads() {
    }
}
