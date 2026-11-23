package com.example.secwatch.web

import com.example.secwatch.support.FakeExternalApiIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties

@DisplayName("업로드 크기 설정")
class UploadLimitConfigTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var multipartProperties: MultipartProperties
    @Autowired lateinit var serverProperties: ServerProperties

    @Test
    @DisplayName("한도 안의 파일과 한도 안의 붙여넣은 내용을 함께 보내도 요청 한도에 걸리지 않는다")
    fun request_limit_covers_a_full_file_and_full_pasted_text() {
        val file = multipartProperties.maxFileSize.toBytes()
        val pasted = serverProperties.tomcat.maxHttpFormPostSize.toBytes()

        assertThat(multipartProperties.maxRequestSize.toBytes()).isGreaterThan(file + pasted)
    }
}
