package com.example.secwatch.notify

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

@DisplayName("디스코드 전송")
class DiscordNotifierTest {
    private val webhookUrl = "https://discord.com/api/webhooks/123456/secret-token"
    private val requestUrl = "$webhookUrl?wait=true"
    private val createdMessage = withSuccess("""{"id":"1"}""", MediaType.APPLICATION_JSON)
    private lateinit var server: MockRestServiceServer
    private lateinit var discordNotifier: DiscordNotifier
    private val logAppender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(DiscordNotifier::class.java) as Logger

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder()
        server = MockRestServiceServer.bindTo(restClientBuilder).build()
        discordNotifier = DiscordNotifier(restClientBuilder.build())
        logAppender.start()
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    private fun exceptionChain(e: Throwable): String =
        generateSequence(e) { throwable -> throwable.cause }.joinToString("\n") { throwable -> throwable.toString() }

    @Test
    @DisplayName("메시지를 content로 웹훅에 보낸다")
    fun posts_message_as_content() {
        server.expect(requestTo(requestUrl))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.content").value("hello"))
            .andRespond(createdMessage)

        discordNotifier.send(webhookUrl, "hello")

        server.verify()
    }

    @Test
    @DisplayName("저장 확인을 기다리도록 wait=true를 붙여서 보낸다")
    fun waits_for_server_confirmation() {
        server.expect(requestTo("$webhookUrl?wait=true"))
            .andRespond(createdMessage)

        discordNotifier.send(webhookUrl, "hello")

        server.verify()
    }

    @Test
    @DisplayName("주소에 있던 다른 쿼리는 지키고 wait는 true로 맞춘다")
    fun keeps_existing_query_and_forces_wait_true() {
        server.expect(requestTo("$webhookUrl?thread_id=99&wait=true"))
            .andRespond(createdMessage)

        discordNotifier.send("$webhookUrl?thread_id=99&wait=false", "hello")

        server.verify()
    }

    @Test
    @DisplayName("메시지 본문 없이 성공 응답이 오면 전송 실패로 바꾼다")
    fun success_without_message_body_is_a_failure() {
        server.expect(requestTo(requestUrl)).andRespond(withNoContent())

        assertThrows<NotifyFailedException> { discordNotifier.send(webhookUrl, "x") }
    }

    @Test
    @DisplayName("멘션이 울리지 않도록 allowed_mentions를 비워서 보낸다")
    fun mentions_are_disabled() {
        server.expect(requestTo(requestUrl))
            .andExpect(jsonPath("$.content").value("@everyone 긴급"))
            .andExpect(jsonPath("$.allowed_mentions.parse").isArray)
            .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty)
            .andRespond(createdMessage)

        discordNotifier.send(webhookUrl, "@everyone 긴급")

        server.verify()
    }

    @Test
    @DisplayName("2000자를 넘는 내용은 잘라서 보낸다")
    fun content_is_cut_to_2000_chars() {
        server.expect(requestTo(requestUrl))
            .andExpect(jsonPath("$.content").value("a".repeat(2000)))
            .andRespond(createdMessage)

        discordNotifier.send(webhookUrl, "a".repeat(2500))

        server.verify()
    }

    @Test
    @DisplayName("전송이 실패해도 예외의 메시지와 원인에 웹훅 주소가 드러나지 않는다")
    fun failure_throws_without_leaking_url_in_message_or_cause() {
        server.expect(requestTo(requestUrl)).andRespond(withServerError())

        val exception = assertThrows<NotifyFailedException> { discordNotifier.send(webhookUrl, "x") }

        assertThat(exceptionChain(exception)).doesNotContain("secret-token").doesNotContain("discord.com/api/webhooks")
    }

    @Test
    @DisplayName("전송이 실패해도 로그에 웹훅 주소가 남지 않는다")
    fun failure_does_not_log_the_url() {
        server.expect(requestTo(requestUrl)).andRespond(withServerError())

        assertThrows<NotifyFailedException> { discordNotifier.send(webhookUrl, "x") }

        val logged = logAppender.list.joinToString("\n") { event -> event.formattedMessage + (event.throwableProxy?.message ?: "") }
        assertThat(logged).isNotBlank().doesNotContain("secret-token")
    }

    @Test
    @DisplayName("지워진 웹훅(404)도 전송 실패로 바꾼다")
    fun deleted_webhook_is_a_failure() {
        server.expect(requestTo(requestUrl)).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThrows<NotifyFailedException> { discordNotifier.send(webhookUrl, "x") }
    }

    @Test
    @DisplayName("주소 형식이 잘못되어도 전송 실패로 바꾸고 주소를 드러내지 않는다")
    fun malformed_url_is_a_failure_without_leaking_it() {
        val malformedUrl = "https://discord.com/api/webhooks/ secret-token"

        val exception = assertThrows<NotifyFailedException> { discordNotifier.send(malformedUrl, "x") }

        assertThat(exceptionChain(exception)).doesNotContain("secret-token")
        val logged = logAppender.list.joinToString("\n") { event -> event.formattedMessage }
        assertThat(logged).doesNotContain("secret-token")
    }
}
