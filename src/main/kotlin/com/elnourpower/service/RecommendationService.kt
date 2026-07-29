package com.elnourpower.service

import com.elnourpower.model.*
import com.elnourpower.repository.BatteryCatalog
import com.elnourpower.repository.PowerSourceCatalog
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.min

/**
 * Cœur métier: à partir du profil de consommation (3 jours) et de la durée de
 * coupure visée (48h par défaut), propose:
 *   - le nombre de batteries pour couvrir l' autonomité nuit + coupure
 *   - une source d'énergie pour recharger (solaire prioritaire, gaz en secours)
 *   - un budget achat et une option location
 *   - une estimation STEG (facture + aide PROSOL + temps de retour)
 */
@Service
class RecommendationService(
    private val batteries: BatteryCatalog,
    private val sources: PowerSourceCatalog,
    private val steg: StegService
) {

    fun recommend(profile: PowerProfile, outageHours: Int, preferRent: Boolean): Recommendation {
        // Énergie à stocker pour tenir [outageHours] sans recharge.
        // On ajoute 20% de marge (rendement onduleur + vieillissement).
        val energyWh = profile.totalTwoDaysWh * (outageHours / 48.0) * 1.2

        // === Batterie ===
        // Stratégie: choisir la batterie qui minimise le coût total pour couvrir
        // l'énergie requise. On teste toutes les références et on garde la moins chère.
        val (battery, count) = batteries.all().map { b ->
            b to ceil(energyWh / 1000.0 / b.usableKwh).toInt().coerceAtLeast(1)
        }.minByOrNull { (b, c) -> b.priceTnd * c }!!

        val totalKwh = count * battery.usableKwh
        val autonomyHours = (totalKwh * 1000.0 / (profile.averageDailyKwh * 1000.0 / 24.0)).let { Math.round(it * 10.0) / 10.0 }
        val batteryPick = BatteryPick(
            battery = battery,
            count = count,
            totalUsableKwh = totalKwh,
            autonomyHours = autonomyHours,
            totalTnd = count * battery.priceTnd,
            coversOutage = autonomyHours >= outageHours
        )

        // === Source d'énergie ===
        // Puissance de recharge requise: recharger les batteries en ~4h de soleil
        // + alimenter la conso jour.
        val rechargeKw = (totalKwh / 4.0) + (profile.dayKwhAvg / PowerNeed.DAY_HOURS)
        val peakKw = profile.peakPowerW / 1000.0
        val neededKw = min(maxOf(rechargeKw, peakKw, 3.0), 12.0)

        val sourcePicks = pickSources(neededKw, preferRent)
        val totalInvestment = batteryPick.totalTnd +
            sourcePicks.filter { !preferRent }.sumOf { it.source.purchaseTnd * it.units }

        val monthlyOption = sourcePicks.sumOf { it.monthlyTnd } +
            sourcePicks.sumOf { it.source.maintenancePerYearTnd * it.units / 12.0 }

        // === STEG + PROSOL ===
        // Puissance solaire installée = somme des kits solaire choisis
        val solarInstalledKw = sourcePicks
            .filter { it.source.kind == PowerSourceKind.SOLAR_KIT || it.source.kind == PowerSourceKind.HYBRID }
            .sumOf { it.source.powerKw * it.units }
        val solarCost = if (!preferRent) sourcePicks
            .filter { it.source.kind == PowerSourceKind.SOLAR_KIT || it.source.kind == PowerSourceKind.HYBRID }
            .sumOf { it.source.purchaseTnd * it.units }
        else 0.0

        val stegEstimate = computeSteg(profile, solarInstalledKw, solarCost)

        val summary = buildSummary(profile, batteryPick, sourcePicks, outageHours, stegEstimate)

        return Recommendation(
            profile = profile,
            battery = batteryPick,
            powerSources = sourcePicks,
            steg = stegEstimate,
            outageHours = outageHours,
            totalInvestmentTnd = round(totalInvestment),
            monthlyOptionTnd = round(monthlyOption),
            summary = summary
        )
    }

    private fun computeSteg(
        profile: PowerProfile,
        solarInstalledKw: Double,
        solarCostTnd: Double
    ): StegEstimate {
        val monthlyKwh = profile.averageDailyKwh * 30.0
        val monthlyNow = steg.monthlyBill(monthlyKwh)
        val yearlyNow = steg.yearlyBill(profile.averageDailyKwh)

        // Autoconsommation solaire: couvre ~70% de la conso (typique Tunisie)
        val remainingMonthlyKwh = monthlyKwh * 0.30
        val monthlyWithSolar = if (solarInstalledKw > 0) steg.monthlyBill(remainingMonthlyKwh) else monthlyNow
        val yearlySavings = if (solarInstalledKw > 0) (yearlyNow - monthlyWithSolar * 12) else 0.0

        val prosol = if (solarInstalledKw > 0 && solarCostTnd > 0)
            steg.prosolIncentive(solarInstalledKw, solarCostTnd)
        else StegService.ProsolIncentive(0.0, 0.0, 0.0, 0.0, 0)

        // Temps de retour = (investissement solaire - aide immédiate) / économies annuelles
        val netInvestment = (solarCostTnd - prosol.totalImmediateSavings).coerceAtLeast(0.0)
        val paybackYears = if (yearlySavings > 0) round1(netInvestment / yearlySavings) else 0.0

        return StegEstimate(
            monthlyBillNowTnd = round2(monthlyNow),
            yearlyBillNowTnd = round2(yearlyNow),
            monthlyBillWithSolarTnd = round2(monthlyWithSolar),
            yearlySavingsTnd = round2(yearlySavings),
            prosol = prosol,
            paybackYears = paybackYears
        )
    }

    private fun pickSources(neededKw: Double, preferRent: Boolean): List<PowerSourcePick> {
        // 1) Kit solaire dimensionné
        val solar = sources.solarKits().minByOrNull { Math.abs(it.powerKw - neededKw) }
            ?: sources.solarKits().last()
        val solarUnits = ceil(neededKw / solar.powerKw).toInt().coerceAtLeast(1)

        // 2) Secours gaz pour la nuit / jours sans soleil
        val gas = sources.generators().first { it.id == "gen_gaz_5kw" }

        val solarMonthly = if (preferRent) solar.monthlyRentTnd * solarUnits else 0.0
        val gasMonthly = if (preferRent) gas.monthlyRentTnd else 0.0

        return listOf(
            PowerSourcePick(
                source = solar,
                units = solarUnits,
                canRechargeBatteries = true,
                monthlyTnd = solarMonthly + solar.maintenancePerYearTnd * solarUnits / 12.0,
                rationale = "Source principale: recharge les batteries et alimente la maison le jour."
            ),
            PowerSourcePick(
                source = gas,
                units = 1,
                canRechargeBatteries = true,
                monthlyTnd = gasMonthly + gas.maintenancePerYearTnd / 12.0,
                rationale = "Secours nuit / temps couvert: recharge batterie en ~4h si besoin."
            )
        )
    }

    private fun buildSummary(
        profile: PowerProfile,
        bp: BatteryPick,
        sources: List<PowerSourcePick>,
        outageHours: Int,
        steg: StegEstimate
    ): String = buildString {
        appendLine("Consommation moyenne: ${profile.averageDailyKwh} kWh/jour " +
            "(${profile.dayKwhAvg} jour / ${profile.nightKwhAvg} nuit), pic ${profile.peakPowerW} W.")
        appendLine("Pour tenir ${outageHours}h de coupure: ${bp.count} × ${bp.battery.brand} ${bp.battery.model} " +
            "= ${bp.totalUsableKwh} kWh utiles → autonomie ${bp.autonomyHours}h " +
            "(${if (bp.coversOutage) "OK ✅" else "insuffisant ⚠"}).")
        appendLine("Facture STEG: ~${steg.monthlyBillNowTnd} DT/mois actuellement" +
            (if (steg.yearlySavingsTnd > 0) " → ~${steg.monthlyBillWithSolarTnd} DT/mois après solaire" else "") +
            ". Économie: ${steg.yearlySavingsTnd} DT/an.")
        if (steg.prosol.totalImmediateSavings > 0) {
            appendLine("Aide PROSOL ELEC: ${steg.prosol.totalImmediateSavings} DT immédiats" +
                " + crédit STEG ${steg.prosol.creditMonthly} DT/mois sur ${steg.prosol.creditYears} ans.")
            appendLine("Retour sur investissement solaire: ~${steg.paybackYears} ans.")
        }
        sources.forEach {
            appendLine("- ${it.source.name} × ${it.units}: ${it.rationale}")
        }
    }

    private fun round(v: Double) = BigDecimal(v).setScale(0, RoundingMode.HALF_UP).toDouble()
    private fun round1(v: Double) = BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toDouble()
    private fun round2(v: Double) = BigDecimal(v).setScale(2, RoundingMode.HALF_UP).toDouble()
}
