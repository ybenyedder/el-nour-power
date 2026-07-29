package com.elnourpower.service

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

/**
 * Appelle le microservice scraper pour récupérer les vrais produits AliExpress /
 * Alibaba (titre, photo, prix), et enrichit chaque produit avec l'autodétection
 * catégorie + puissance (réutilisée depuis ProductImportService).
 */
@Service
class ProductSearchService(
    @Qualifier("scraperRestTemplate") private val rest: RestTemplate,
    private val productImport: ProductImportService,
    @Value("\${scraper.url:http://localhost:5000}") private val scraperUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class SearchProduct(
        val title: String,
        val url: String?,
        val image: String?,
        val price: String?,         // prix brut tel que scrapé ("€65,9")
        val source: String,
        val category: String,       // catégorie devinée
        val categoryLabel: String,
        val detectedPowerWatts: Double?,
        val matchedCatalogId: String?
    )

    data class SearchResult(
        val query: String,
        val source: String,
        val count: Int,
        val tookMs: Int,
        val cached: Boolean,
        val products: List<SearchProduct>
    )

    fun search(query: String, source: String = "aliexpress", limit: Int = 20): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return SearchResult(trimmed, source, 0, 0, false, emptyList())
        }
        val url = "$scraperUrl/search?q=${enc(trimmed)}&source=$source&limit=${limit.coerceIn(1, 40)}"
        return try {
            val node = rest.getForObject(url, JsonNode::class.java)
            val products = mutableListOf<SearchProduct>()
            node?.path("products")?.forEach { p ->
                val title = p.path("title").asText("")
                if (title.isBlank()) return@forEach
                // Autodétection depuis le titre du produit scrapé
                val detected = productImport.analyze(title)
                products += SearchProduct(
                    title = title,
                    url = p.path("url").asText(null),
                    image = p.path("image").asText(null),
                    price = p.path("price").asText(null),
                    source = p.path("source").asText(source),
                    category = detected.category.name,
                    categoryLabel = detected.categoryLabel,
                    detectedPowerWatts = detected.detectedPowerWatts,
                    matchedCatalogId = detected.matchedCatalogId
                )
            }
            SearchResult(
                query = trimmed,
                source = source,
                count = products.size,
                tookMs = node?.path("took_ms")?.asInt(0) ?: 0,
                cached = false,
                products = products
            )
        } catch (e: Exception) {
            log.warn("Scraper indisponible pour '{}': {} — {}", trimmed, e.javaClass.simpleName, e.message)
            SearchResult(trimmed, source, 0, 0, false, emptyList())
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
