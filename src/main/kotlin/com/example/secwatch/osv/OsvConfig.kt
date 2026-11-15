package com.example.secwatch.osv

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class OsvConfig {
    @Bean
    fun osvClient(
        restClientBuilder: RestClient.Builder,
        @Value("\${secwatch.osv.base-url:https://api.osv.dev}") baseUrl: String,
    ): OsvClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(20))
        }
        return OsvClient(restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build())
    }
}
