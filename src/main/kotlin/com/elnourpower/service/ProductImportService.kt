package com.elnourpower.service

import com.elnourpower.model.ApplianceCategory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Import d'une fiche produit depuis une URL ou un titre collé par l'utilisateur.
 *
 * AliExpress / Alibaba servent leurs pages en CSR (rendu côté client) avec
 * détection anti-bot : le HTML initial ne contient pas les specs produit.
 * Ce service utilise donc deux leviers :
 *
 *   1. Extraction du titre depuis la page (quand il est présent dans les balises
 *      meta og:title / <title>, ce qui marche sur de nombreux marchands moins
 *      protégés et parfois sur AliExpress selon le produit/la région).
 *   2. Autodétection intelligente de la catégorie + de la puissance à partir du
 *      texte (URL décodée, titre collé, ou titre extrait). Ça marche dans 100 %
 *      des cas où l'utilisateur colle au moins le titre du produit.
 *
 * L'utilisateur confirme ensuite la fiche pré-remplie avant de l'ajouter.
 */
@Service
class ProductImportService(
    private val rest: RestTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class ImportedProduct(
        val source: String,            // "aliexpress" | "alibaba" | "steg" | "manual" | "unknown"
        val sourceUrl: String?,        // URL d'origine si fournie
        val rawText: String,           // texte analysé
        val title: String,             // titre du produit (nettoyé)
        val category: ApplianceCategory,
        val categoryLabel: String,
        val detectedPowerWatts: Double?,  // puissance détectée (null si non trouvée)
        val matchedCatalogId: String?,   // ID du catalogue si reconnu
        val confidence: Double,          // 0..1
        val detectionDetails: List<String>  // ce qui a été détecté et comment
    )

    /**
     * Analyse une saisie utilisateur : URL, titre de produit, ou description libre.
     */
    fun analyze(input: String): ImportedProduct {
        val trimmed = input.trim()
        val details = mutableListOf<String>()
        var source = "manual"
        var sourceUrl: String? = null
        var textToAnalyze = trimmed

        // 1. Détection de la source + extraction du titre si URL
        if (looksLikeUrl(trimmed)) {
            sourceUrl = normalizeUrl(trimmed)
            source = detectSource(sourceUrl)
            details += "URL détectée ($source)"
            val extractedTitle = tryFetchTitle(sourceUrl)
            if (extractedTitle != null) {
                textToAnalyze = extractedTitle
                details += "Titre extrait de la page : \"${extractedTitle.take(80)}…\""
            } else {
                // fallback : le texte dans l'URL décodée contient souvent le titre
                val fromUrl = titleFromUrl(sourceUrl)
                if (fromUrl.isNotBlank()) {
                    textToAnalyze = fromUrl
                    details += "Titre déduit de l'URL : \"${fromUrl.take(80)}\""
                } else {
                    details += "Page protégée (CSR/anti-bot) — analyse du texte de l'URL uniquement"
                }
            }
        } else {
            details += "Texte libre analysé"
        }

        // 2. Autodétection catégorie
        val (category, catConfidence, catHit) = detectCategory(textToAnalyze)
        if (catHit != null) details += "Catégorie : $catHit (${(catConfidence * 100).toInt()}%)"

        // 3. Autodétection puissance
        val (power, powerHit) = detectPower(textToAnalyze)
        if (powerHit != null) details += "Puissance : $powerHit"
        else details += "Puissance : non détectée (à saisir)"

        // 4. Recherche dans le catalogue
        val matchedId = matchCatalog(category, power, textToAnalyze)
        if (matchedId != null) details += "Appareil catalogue correspondant : $matchedId"

        val confidence = ((if (catHit != null) 0.5 else 0.0) + (if (power != null) 0.4 else 0.0) + (if (matchedId != null) 0.1 else 0.0))
            .coerceAtMost(1.0)

        return ImportedProduct(
            source = source,
            sourceUrl = sourceUrl,
            rawText = trimmed,
            title = cleanTitle(textToAnalyze).ifBlank { trimmed.take(100) },
            category = category,
            categoryLabel = category.name,
            detectedPowerWatts = power,
            matchedCatalogId = matchedId,
            confidence = confidence,
            detectionDetails = details
        )
    }

    // ===== Détection URL / source =====
    private fun looksLikeUrl(s: String): Boolean =
        s.startsWith("http://", true) || s.startsWith("https://", true) ||
            (s.contains('.') && !s.contains(' ') && s.length > 15)

    private fun normalizeUrl(s: String): String =
        if (s.startsWith("http", true)) s else "https://$s"

    private fun detectSource(url: String): String {
        val host = try { URI(url).host.lowercase() } catch (_: Exception) { "" }
        return when {
            "aliexpress" in host -> "aliexpress"
            "alibaba" in host -> "alibaba"
            "steg" in host -> "steg"
            "amazon" in host -> "amazon"
            else -> "unknown"
        }
    }

    private fun titleFromUrl(url: String): String {
        // AliExpress : /item/12345.html — pas de titre dans l'URL.
        // Alibaba : /product-detail/Titre-Du-Produit_605.html
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8)
        val m = Pattern.compile("/product-detail/([^/_?]+(?:_[^/_?]+)*)").matcher(decoded)
        if (m.find()) {
            return m.group(1).replace("-", " ").replace("_", " ").trim()
        }
        // sinon extraire le dernier segment de chemin
        val path = try { URI(url).path } catch (_: Exception) { "" }
        val last = path.substringAfterLast('/').substringBeforeLast('.')
        return if (last.length > 4) last.replace("-", " ").replace("_", " ").replace("+", " ").trim() else ""
    }

    private fun tryFetchTitle(url: String): String? {
        return try {
            val headers = org.springframework.http.HttpHeaders().apply {
                add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0 Safari/537.36")
                add("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            }
            val req = org.springframework.http.RequestEntity
                .method(org.springframework.http.HttpMethod.GET, URI(url))
                .headers(headers)
                .build()
            val resp = rest.exchange(req, String::class.java)
            val html = resp.body ?: return null
            extractTitleFromHtml(html)
        } catch (e: Exception) {
            log.debug("Fetch titre échoué pour {}: {}", url, e.message)
            null
        }
    }

    private fun extractTitleFromHtml(html: String): String? {
        // og:title (le plus fiable quand présent)
        val og = Regex("""<meta[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""").find(html)
        if (og != null) return og.groupValues[1].trim()
        // <title>
        val title = Regex("""<title[^>]*>([^<]+)</title>""").find(html)
        if (title != null) {
            val t = title.groupValues[1].trim()
            // filtrer les titres génériques type "AliExpress -..."
            if (t.length > 10 && !t.contains("AliExpress", true) && !t.contains("404", true)) return t
        }
        return null
    }

    // ===== Autodétection catégorie =====
    // Mots-clés (FR/EN) → catégorie. Le premier match gagne, par ordre de spécificité.
    private val categoryKeywords: List<Pair<List<String>, ApplianceCategory>> = listOf(
        listOf("climatiseur", "climatisation", "clim ", "air conditioner", "split", "btu") to ApplianceCategory.COOLING,
        listOf("ventilateur", "fan", "brasseur") to ApplianceCategory.COOLING,
        listOf("réfrigérateur", "refrigerateur", "frigo", "frigidaire", "fridge", "refrigerator") to ApplianceCategory.COLD,
        listOf("congélateur", "congelateur", "freezer", "coffre congel") to ApplianceCategory.COLD,
        listOf("lave-linge", "lave linge", "machine à laver", "machine a laver", "washing machine", "washer") to ApplianceCategory.LAUNDRY,
        listOf("lave-vaisselle", "lave vaisselle", "dishwasher") to ApplianceCategory.LAUNDRY,
        listOf("sèche-linge", "seche linge", "dryer", "tumble dryer") to ApplianceCategory.LAUNDRY,
        listOf("four", "oven", "cuisinière", "cuisiniere", "stove") to ApplianceCategory.COOKING,
        listOf("micro-ondes", "micro ondes", "microwave") to ApplianceCategory.COOKING,
        listOf("plaque", "induction", "hotte", "hob", "cooktop") to ApplianceCategory.COOKING,
        listOf("chauffe-eau", "chauffe eau", "water heater", "chauffe bain", "ballon") to ApplianceCategory.WATER,
        listOf("pompe", "surpresseur", "pump", "water pump") to ApplianceCategory.WATER,
        listOf("téléviseur", "televiseur", "tv ", "television", "écran", "ecran", "smart tv") to ApplianceCategory.ELECTRONICS,
        listOf("ordinateur", "pc ", "laptop", "ordinateur portable", "desktop") to ApplianceCategory.ELECTRONICS,
        listOf("console", "playstation", "xbox", "nintendo") to ApplianceCategory.ELECTRONICS,
        listOf("routeur", "box", "modem", "wifi", "répéteur") to ApplianceCategory.ELECTRONICS,
        listOf("ampoule", "led", "éclairage", "eclairage", "lampe", "lighting", "light bulb", "halogène", "halogene") to ApplianceCategory.LIGHTING,
        listOf("fer à repasser", "fer a repasser", "iron", "aspirateur", "vacuum") to ApplianceCategory.OTHER
    )

    private fun detectCategory(text: String): Triple<ApplianceCategory, Double, String?> {
        val lower = text.lowercase()
        for ((keywords, cat) in categoryKeywords) {
            val hit = keywords.firstOrNull { kw -> lower.contains(kw) }
            if (hit != null) return Triple(cat, 0.95, hit)
        }
        return Triple(ApplianceCategory.OTHER, 0.0, null)
    }

    // ===== Autodétection puissance =====
    private fun detectPower(text: String): Pair<Double?, String?> {
        val lower = text.lowercase()
        // BTU → Watts : 1 BTU/h ≈ 0.293 W. Souvent pour les clim.
        val btu = Regex("""(\d[\d\s.,]+)\s*(?:btu|btu/h|btuh)""").find(lower)
        if (btu != null) {
            val n = btu.groupValues[1].replace(Regex("[\\s.,]"), "").toDoubleOrNull()
            if (n != null && n in 5000.0..60000.0) {
                val watts = n * 0.293
                return watts to "${n.toInt()} BTU → ${watts.toInt()} W"
            }
        }
        // X kW / Kilowatt
        val kw = Regex("""(\d+(?:[.,]\d+)?)\s*k\s*w""").find(lower)
        if (kw != null) {
            val n = kw.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (n != null && n in 0.01..20.0) return (n * 1000) to "${kw.groupValues[0]} → ${(n*1000).toInt()} W"
        }
        // X W / Watt (mais pas "wifi", "watts" isolé ok)
        val w = Regex("""(\d{2,5})\s*w(?:att)?\b""").find(lower)
        if (w != null) {
            val n = w.groupValues[1].toDoubleOrNull()
            if (n != null && n in 5.0..15000.0) return n to "${n.toInt()} W"
        }
        // X V (volts) — infos complémentaire, on n'en fait pas une puissance
        return null to null
    }

    // ===== Recherche catalogue =====
    private val catalogHints = listOf(
        "clim_12000" to listOf("12000 btu"),
        "clim_18000" to listOf("18000 btu", "18000btu"),
        "clim_24000" to listOf("24000 btu", "24000btu"),
        "frigo_a" to listOf("réfrigérateur a+", "frigo a+", "classe a+"),
        "frigo_old" to listOf("ancien", "classe c", "classe d"),
        "lave_linge" to listOf("lave-linge", "lave linge", "washing"),
        "four_elec" to listOf("four électrique", "four electrique"),
        "tv_led" to listOf("tv led", "téléviseur led"),
        "chauffe_eau" to listOf("chauffe-eau", "chauffe eau")
    )

    private fun matchCatalog(cat: ApplianceCategory, power: Double?, text: String): String? {
        val lower = text.lowercase()
        for ((id, hints) in catalogHints) {
            if (hints.any { lower.contains(it) }) return id
        }
        // fallback : catégorie + tranche de puissance
        if (power != null) {
            return when (cat) {
                ApplianceCategory.COOLING -> when {
                    power < 1300 -> "clim_12000"
                    power < 2100 -> "clim_18000"
                    else -> "clim_24000"
                }
                ApplianceCategory.COLD -> if (power > 250) "frigo_old" else "frigo_a"
                ApplianceCategory.LAUNDRY -> "lave_linge"
                ApplianceCategory.COOKING -> "four_elec"
                ApplianceCategory.ELECTRONICS -> "tv_led"
                else -> null
            }
        }
        return null
    }

    private fun cleanTitle(s: String): String =
        s.replace(Regex("\\s+"), " ").trim().take(120)
}
