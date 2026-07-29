package com.elnourpower.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class RestConfig {

    /** RestTemplate standard (météo, etc.) — timeouts courts. */
    @Bean
    @Primary
    fun restTemplate(@Suppress("UNUSED_PARAMETER") builder: RestTemplateBuilder): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(8_000)
            setReadTimeout(15_000)
        }
        return RestTemplate(factory)
    }

    /**
     * RestTemplate dédié au scraper : timeouts longs car le scraping AliExpress
     * via Playwright prend 8-30s (chargement page + attente rendu JS).
     */
    @Bean(name = ["scraperRestTemplate"])
    fun scraperRestTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(10_000)
            setReadTimeout(90_000)
        }
        return RestTemplate(factory)
    }
}
