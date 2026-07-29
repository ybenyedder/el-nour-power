package com.elnourpower.repository

import com.elnourpower.model.PowerSource
import com.elnourpower.model.PowerSourceKind.*
import org.springframework.stereotype.Component

/**
 * Sources d'énergie de secours — données marché Tunisie 2026.
 * Solaire: ~2900 TND/kWc posé (Protunisie, Gigavolt, Gamco).
 * Générateur gaz: ~4000 TND achat, ~500 TND/an maintenance (EPST).
 */
@Component
class PowerSourceCatalog {

    private val all: List<PowerSource> = listOf(
        PowerSource(
            id = "solar_3kw",
            kind = SOLAR_KIT,
            name = "Kit solaire 3 kWc + onduleur hybride",
            powerKw = 3.0,
            fuelType = "Soleil",
            runtimeHours = 0,
            purchaseTnd = 9000.0,
            monthlyRentTnd = 180.0,
            maintenancePerYearTnd = 250.0,
            co2PerKgKwh = 0.0,
            notes = "Recharge les batteries le jour. 4-5 h de soleil utile/jour en Tunisie.",
            sourceUrl = "https://protunisie.com/energie-solaire/installation-photovoltaique/prix-photovoltaique-en-tunisie/"
        ),
        PowerSource(
            id = "solar_5kw",
            kind = SOLAR_KIT,
            name = "Kit solaire 5 kWc + onduleur hybride",
            powerKw = 5.0,
            fuelType = "Soleil",
            runtimeHours = 0,
            purchaseTnd = 14000.0,
            monthlyRentTnd = 260.0,
            maintenancePerYearTnd = 300.0,
            co2PerKgKwh = 0.0,
            notes = "Idéal recharge batterie + autoconso. Couvre la clim en été.",
            sourceUrl = "https://www.gamco-energy.com/installation-photovoltaique-a-tunis-guide-complet-et-prix-2026/"
        ),
        PowerSource(
            id = "solar_8kw",
            kind = SOLAR_KIT,
            name = "Kit solaire 8 kWc + onduleur hybride",
            powerKw = 8.0,
            fuelType = "Soleil",
            runtimeHours = 0,
            purchaseTnd = 22500.0,
            monthlyRentTnd = 420.0,
            maintenancePerYearTnd = 380.0,
            co2PerKgKwh = 0.0,
            notes = "Grande villa avec plusieurs clim. Recharge complète batterie en 3h.",
            sourceUrl = "https://gigavolt-energy.com/prix-des-panneaux-solaires.html"
        ),
        PowerSource(
            id = "gen_gaz_5kw",
            kind = GAS_GENERATOR,
            name = "Groupe électrogène gaz 5 kW portatif",
            powerKw = 5.0,
            fuelType = "Gaz (GPL)",
            runtimeHours = 9,
            purchaseTnd = 4200.0,
            monthlyRentTnd = 220.0,
            maintenancePerYearTnd = 500.0,
            co2PerKgKwh = 0.55,
            notes = "Location possible. ~1.2 kg GPL/heure. Bruit modéré.",
            sourceUrl = "https://www.epst.tn/groupes-electrogenes-solaires/"
        ),
        PowerSource(
            id = "gen_gaz_10kw",
            kind = GAS_GENERATOR,
            name = "Groupe électrogène gaz 10 kW inverter",
            powerKw = 10.0,
            fuelType = "Gaz (GPL)",
            runtimeHours = 12,
            purchaseTnd = 8800.0,
            monthlyRentTnd = 380.0,
            maintenancePerYearTnd = 750.0,
            co2PerKgKwh = 0.55,
            notes = "Couvre toute la maison clim comprise. Inverter = silence + propre.",
            sourceUrl = "https://www.epst.tn/groupes-electrogenes-solaires/"
        ),
        PowerSource(
            id = "hybrid_6kw",
            kind = HYBRID,
            name = "Kit hybride 6 kW (solaire + secours gaz)",
            powerKw = 6.0,
            fuelType = "Solaire + Gaz",
            runtimeHours = 0,
            purchaseTnd = 19000.0,
            monthlyRentTnd = 350.0,
            maintenancePerYearTnd = 500.0,
            co2PerKgKwh = 0.12,
            notes = "Le meilleur des deux: solaire prioritaire, gaz en cas de besoin.",
            sourceUrl = "https://solynenergy.com/panneaux-solaires-vs-groupe-electrogene-tunisie/"
        )
    )

    fun all(): List<PowerSource> = all
    fun generators(): List<PowerSource> = all.filter { it.kind == GAS_GENERATOR }
    fun solarKits(): List<PowerSource> = all.filter { it.kind == SOLAR_KIT }
    fun hybrids(): List<PowerSource> = all.filter { it.kind == HYBRID }
    fun byId(id: String): PowerSource? = all.find { it.id == id }
}
