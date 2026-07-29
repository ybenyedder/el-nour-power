package com.elnourpower.service

import com.elnourpower.model.Appliance
import org.springframework.stereotype.Service
import java.net.URLEncoder

/**
 * Génère des liens de recherche produits pour aider le client à identifier
 * précisément le modèle d'un appareil qu'il possède, sur 3 sources :
 *   - Alibaba   (gros / fournisseurs)
 *   - AliExpress (détail / particulier)
 *   - STEG       (programme PROSOL + tarifs officiels)
 *
 * On ne peut pas interroger l'API produit Alibaba/AliExpress sans compte
 * vendeur : on construit donc des URLs de recherche ciblées, ce qui répond au
 * besoin « aider à identifier ce qu'on a chez soi ».
 */
@Service
class ProductLinkService {

    private val stegUrls = mapOf(
        "tarifs" to "https://www.steg.com.tn/fr/page/les-tarifs-d%C3%A9lectricit%C3%A9",
        "prosol" to "https://www.anme.tn/fr/project/prosol-elec-economique",
        "confort" to "https://www.steg.com.tn/fr/info/confort"
    )

    fun stegLinks(): Map<String, String> = stegUrls

    /** Liens de recherche produits pour un appareil donné. */
    fun linksFor(a: Appliance): ProductLinks {
        val q = query(a)
        return ProductLinks(
            query = q,
            alibaba = "https://www.alibaba.com/trade/search?SearchText=${enc(q)}",
            aliexpress = "https://www.aliexpress.com/wholesale?SearchText=${enc(q)}",
            steg = stegUrlFor(a)
        )
    }

    /** Requête de recherche la plus discriminante (nom + puissance + catégorie). */
    private fun query(a: Appliance): String {
        val tokens = mutableListOf(a.name.replace(Regex("\\d+ BTU"), { mr ->
            // « Climatiseur 18000 BTU » → « climatiseur 18000 BTU » (gardé tel quel)
            mr.value
        }))
        tokens += "${a.powerWatts.toInt()}W"
        return tokens.joinToString(" ")
    }

    private fun stegUrlFor(a: Appliance): String = when (a.category) {
        com.elnourpower.model.ApplianceCategory.COOLING ->
            "https://www.steg.com.tn/fr/info/confort" // conseil climatisation STEG
        com.elnourpower.model.ApplianceCategory.COLD ->
            "https://www.steg.com.tn/fr/info/confort"
        else -> stegUrls["tarifs"]!!
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    data class ProductLinks(
        val query: String,
        val alibaba: String,
        val aliexpress: String,
        val steg: String
    )
}
