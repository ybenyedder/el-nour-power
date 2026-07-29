package com.elnourpower.controller

import com.elnourpower.config.AppProperties
import com.elnourpower.config.currentUserId
import com.elnourpower.model.Appliance
import com.elnourpower.model.EnergyRequest
import com.elnourpower.model.Recommendation
import com.elnourpower.repository.ApplianceCatalog
import com.elnourpower.repository.BatteryCatalog
import com.elnourpower.repository.PowerSourceCatalog
import com.elnourpower.repository.UserApplianceRepository
import com.elnourpower.service.ConsumptionService
import com.elnourpower.service.ProductLinkService
import com.elnourpower.service.ProductImportService
import com.elnourpower.service.ProductSearchService
import com.elnourpower.service.RecommendationService
import com.elnourpower.service.SmartPlugService
import com.elnourpower.service.StegService
import com.elnourpower.service.WeatherService
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api")
class EnergyController(
    private val weather: WeatherService,
    private val consumption: ConsumptionService,
    private val recommendation: RecommendationService,
    private val plugs: SmartPlugService,
    private val productLinks: ProductLinkService,
    private val productImport: ProductImportService,
    private val productSearch: ProductSearchService,
    private val stegService: StegService,
    private val applianceCatalog: ApplianceCatalog,
    private val batteryCatalog: BatteryCatalog,
    private val sourceCatalog: PowerSourceCatalog,
    private val userAppliances: UserApplianceRepository,
    private val props: AppProperties
) {

    @GetMapping("/health")
    fun health() = mapOf(
        "status" to "UP",
        "app" to "el-nour-power",
        "time" to Instant.now().toString()
    )

    @GetMapping("/appliances")
    fun appliances() = applianceCatalog.all()

    @GetMapping("/appliances/links")
    fun applianceLinks() = applianceCatalog.all().associate { it.id to productLinks.linksFor(it) }

    @GetMapping("/batteries")
    fun batteries() = batteryCatalog.all()

    @GetMapping("/sources")
    fun sources() = sourceCatalog.all()

    @GetMapping("/plugs")
    fun smartPlugs() = plugs.readAll()

    @GetMapping("/weather")
    fun weather(@RequestParam(required = false) city: String?) = weather.forecast(city)

    @GetMapping("/steg/links")
    fun stegLinks() = productLinks.stegLinks()

    /**
     * Importe / analyse un produit depuis une URL ou un titre collé.
     * Renvoie une fiche pré-remplie (catégorie + puissance détectées) à confirmer.
     */
    @PostMapping("/products/import")
    fun importProduct(@RequestBody req: ImportRequest): ProductImportService.ImportedProduct =
        productImport.analyze(req.input ?: "")

    data class ImportRequest(val input: String?)

    /**
     * Recherche de vrais produits sur AliExpress / Alibaba via le microservice
     * scraper (Playwright). Renvoie titre + photo + prix + catégorie devinée.
     */
    @GetMapping("/products/search")
    fun searchProducts(
        @RequestParam q: String,
        @RequestParam(defaultValue = "aliexpress") source: String,
        @RequestParam(defaultValue = "20") limit: Int
    ): ProductSearchService.SearchResult =
        productSearch.search(q, source, limit)

    @GetMapping("/steg/bill")
    fun stegBill(@RequestParam monthlyKwh: Double) = mapOf(
        "monthlyTnd" to stegService.monthlyBill(monthlyKwh),
        "yearlyTnd" to stegService.yearlyBill(monthlyKwh / 30.0)
    )

    @GetMapping("/steg/prosol")
    fun prosol(
        @RequestParam installedKw: Double,
        @RequestParam installCostTnd: Double
    ) = stegService.prosolIncentive(installedKw, installCostTnd)

    /**
     * Endpoint principal. Résout les appareils à utiliser dans cet ordre:
     *   1. useSavedInventory → charge l'inventaire sauvegardé du user connecté
     *   2. selections → sélection précise (catalogue + quantité + override puissance)
     *   3. applianceIds → legacy (catalogue seul)
     *   4. customAppliances → appareils hors catalogue
     *   5. défaut → maison type
     */
    @PostMapping("/recommend")
    fun recommend(@RequestBody(required = false) req: EnergyRequest?): Recommendation {
        val r = req ?: EnergyRequest()
        val forecast = weather.forecast(r.city)

        val appliances: List<Appliance> = when {
            r.useSavedInventory -> loadSavedInventory()
            r.selections.isNotEmpty() -> expandSelections(r.selections)
            r.customAppliances.isNotEmpty() -> r.customAppliances
            r.applianceIds.isNotEmpty() -> applianceCatalog.byIds(r.applianceIds)
            else -> applianceCatalog.defaultHome()
        }

        val plugData = if (r.useSmartPlugs) plugs.readAll() else emptyList()
        val profile = consumption.compute(appliances, forecast, plugData)
        val outage = r.outageHours ?: props.outageHours
        return recommendation.recommend(profile, outage, r.preferRent)
    }

    private fun loadSavedInventory(): List<Appliance> {
        val uid = currentUserId() ?: return applianceCatalog.defaultHome()
        val saved = userAppliances.findByUserId(uid)
        if (saved.isEmpty()) return applianceCatalog.defaultHome()
        return saved.flatMap { ua ->
            val base = applianceCatalog.byId(ua.applianceId) ?: return@flatMap emptyList()
            consumption.expandSelections(base, ua.quantity, ua.overridePowerWatts, ua.hoursPerDay)
        }
    }

    private fun expandSelections(selections: List<com.elnourpower.model.ApplianceSelection>): List<Appliance> =
        selections.flatMap { sel ->
            val base = applianceCatalog.byId(sel.applianceId) ?: return@flatMap emptyList()
            consumption.expandSelections(base, sel.quantity, sel.overridePowerWatts, sel.hoursPerDay)
        }
}
