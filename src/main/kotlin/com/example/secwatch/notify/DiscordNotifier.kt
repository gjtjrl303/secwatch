package com.example.secwatch.notify

import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

class DiscordNotifier(private val restClient: RestClient) : Notifier {
    private val log = LoggerFactory.getLogger(DiscordNotifier::class.java)

    /**
     * 예외 메시지와 원인과 로그에 웹훅 주소를 넣지 않는다. 주소가 곧 비밀 토큰이기 때문이다.
     * wait=true를 붙이지 않으면 디스코드는 저장되지 않은 메시지도 오류 없이 응답하므로,
     * 저장 확인을 기다리게 하고 만들어진 메시지가 응답에 없으면 실패로 본다.
     */
    override fun send(webhookUrl: String, message: String) {
        try {
            val createdMessage = restClient.post()
                .uri(UriComponentsBuilder.fromUri(URI.create(webhookUrl)).replaceQueryParam("wait", "true").build(true).toUri())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload(message))
                .retrieve()
                .body(String::class.java)
            if (createdMessage.isNullOrBlank()) {
                log.warn("디스코드가 만들어진 메시지 없이 응답했습니다.")
                throw NotifyFailedException(FAILURE_MESSAGE)
            }
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
