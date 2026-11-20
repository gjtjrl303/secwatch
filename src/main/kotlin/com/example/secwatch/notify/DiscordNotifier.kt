package com.example.secwatch.notify

import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

class DiscordNotifier(private val restClient: RestClient) : Notifier {
    private val log = LoggerFactory.getLogger(DiscordNotifier::class.java)

    /** 예외 메시지와 원인과 로그에 웹훅 주소를 넣지 않는다. 주소가 곧 비밀 토큰이기 때문이다. */
    override fun send(webhookUrl: String, message: String) {
        try {
            restClient.post()
                .uri(URI.create(webhookUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload(message))
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientException) {
            log.warn("디스코드 웹훅 호출에 실패했습니다. 예외={}", e.javaClass.simpleName)
            throw NotifyFailedException(FAILURE_MESSAGE)
        } catch (e: IllegalArgumentException) {
            log.warn("디스코드 웹훅 주소를 사용할 수 없습니다.")
            throw NotifyFailedException(FAILURE_MESSAGE)
        }
    }

    /** 요약은 OSV가 준 외부 값이라 `@everyone` 같은 멘션이 들어 있을 수 있다. 멘션이 울리지 않게 막는다. */
    private fun payload(message: String): Map<String, Any> = mapOf(
        "content" to message.take(MAX_CONTENT_LENGTH),
        "allowed_mentions" to mapOf("parse" to emptyList<String>()),
    )

    private companion object {
        const val MAX_CONTENT_LENGTH = 2000
        const val FAILURE_MESSAGE = "디스코드 전송에 실패했습니다."
    }
}
