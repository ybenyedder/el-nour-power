package com.elnourpower.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculs liés à la STEG (Société Tunisienne de l'Électricité et du Gaz).
 *
 * Tarifs 2026 — basse tension résidentiel, paliers progressifs (millimes/kWh),
 * inchangés depuis plusieurs années. Source : steg.com.tn + Gamco Energy 2026.
 *
 *   0      –  75  kWh/mois   → 181 millimes
 *   76     – 200  kWh/mois   → 223 millimes
 *   201    – 500  kWh/mois   → 338 millimes
 *   > 500       kWh/mois     → 419 millimes
 *
 * Programme PROSOL ELEC (ANME / STEG) :
 *   - Subvention ANME : ~30 % du coût d'installation
 *   - Prime : 1 200 DT/kW installé (autoconso > 1,5 kW), plafonnée à 3 000 DT
 *   - Crédit STEG sur 7 ans à taux préférentiel, remboursable sur la facture
 */
@Service
class StegService {

    private data class Tier(val upToKwh: Int, val millimesPerKwh: Int)

    // Plafonds supérieurs (inclus) de chaque tranche mensuelle
    private val tiers = listOf(
        Tier(75, 181),
        Tier(200, 223),
        Tier(500, 338),
        Tier(Int.MAX_VALUE, 419)
    )

    /** Facture mensuelle estimée (DT) pour une consommation mensuelle en kWh. */
    fun monthlyBill(monthlyKwh: Double): Double {
        var remaining = monthlyKwh
        var previousCap = 0
        var totalMillimes = 0L
        for (t in tiers) {
            if (remaining <= 0) break
            val sliceWidth = (t.upToKwh - previousCap).coerceAtMost(remaining.toInt().coerceAtLeast(0))
            totalMillimes += sliceWidth.toLong() * t.millimesPerKwh
            remaining -= sliceWidth
            previousCap = t.upToKwh
            if (t.upToKwh == Int.MAX_VALUE && remaining > 0) {
                totalMillimes += remaining.toLong() * t.millimesPerKwh
                remaining = 0.0
            }
        }
        // 1 DT = 1000 millimes + TVA 18 % + redevance fixe ~1 DT
        val rawDt = totalMillimes / 1000.0
        val withVat = rawDt * 1.18
        return BigDecimal(withVat + 1.0).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /** Facture annuelle à partir de la conso journalière moyenne. */
    fun yearlyBill(dailyKwhAvg: Double): Double {
        val monthly = dailyKwhAvg * 30.0
        return BigDecimal(monthlyBill(monthly) * 12).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Aide financière PROSOL ELEC pour une installation solaire donnée.
     *
     * @param installedKw puissance crête installée (kWc)
     * @param installCostTnd coût total de l'installation (DT)
     */
    fun prosolIncentive(installedKw: Double, installCostTnd: Double): ProsolIncentive {
        val prime = (installedKw * 1200.0).coerceAtMost(3000.0)
        val anmeSubsidy = installCostTnd * 0.30
        // Crédit STEG: ~montant restant après subvention, étalé sur 7 ans,
        // taux préférentiel ~3% (近似 KAPITALI). On reste indicatif.
        val remainingAfterSubsidy = (installCostTnd - anmeSubsidy).coerceAtLeast(0.0)
        val creditMonthly = remainingAfterSubsidy / (7.0 * 12.0)
        return ProsolIncentive(
            anmeSubsidy = round(anmeSubsidy),
            prime = round(prime),
            totalImmediateSavings = round(anmeSubsidy + prime),
            creditMonthly = round2(creditMonthly),
            creditYears = 7
        )
    }

    data class ProsolIncentive(
        val anmeSubsidy: Double,        // subvention ANME ~30% (DT)
        val prime: Double,              // prime par kW, plafonnée (DT)
        val totalImmediateSavings: Double,
        val creditMonthly: Double,      // mensualité crédit STEG (DT/mois)
        val creditYears: Int
    )

    private fun round(v: Double) = BigDecimal(v).setScale(0, RoundingMode.HALF_UP).toDouble()
    private fun round2(v: Double) = BigDecimal(v).setScale(2, RoundingMode.HALF_UP).toDouble()
}
