package com.elnourpower.model

import com.elnourpower.service.StegService

/**
 * Recommandation complète renvoyée au client: batteries + source d'énergie
 * dimensionnées pour tenir une coupure de [outageHours] heures.
 */
data class Recommendation(
    val profile: PowerProfile,
    val battery: BatteryPick,
    val powerSources: List<PowerSourcePick>,
    val steg: StegEstimate,
    val outageHours: Int,
    val totalInvestmentTnd: Double,
    val monthlyOptionTnd: Double,    // si location choisie
    val summary: String
)

data class BatteryPick(
    val battery: Battery,
    val count: Int,
    val totalUsableKwh: Double,
    val autonomyHours: Double,        // combien d'heures tenues sans recharge
    val totalTnd: Double,
    val coversOutage: Boolean
)

data class PowerSourcePick(
    val source: PowerSource,
    val units: Int,
    val canRechargeBatteries: Boolean,
    val monthlyTnd: Double,           // loyer + carburant/maintenance mensuels
    val rationale: String
)

/**
 * Estimation STEG + PROSOL attachée à une recommandation.
 */
data class StegEstimate(
    val monthlyBillNowTnd: Double,    // facture STEG actuelle estimée
    val yearlyBillNowTnd: Double,
    val monthlyBillWithSolarTnd: Double, // après solaire (autoconso ~70%)
    val yearlySavingsTnd: Double,
    val prosol: StegService.ProsolIncentive,
    val paybackYears: Double          // temps de retour sur investissement
)
