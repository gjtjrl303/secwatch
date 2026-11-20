package com.example.secwatch.notify

import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class NotifyConfig {
    @Bean
    fun discordNotifier(restClientBuilder: RestClient.Builder): Notifier {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(10))
        }
        return DiscordNotifier(restClientBuilder.requestFactory(requestFactory).build())
    }
}
