package com.elnourpower.model

/**
 * Besoin de puissance calculé pour une journée donnée.
 * Séparation jour / nuit pour dimensionner batteries et panneaux.
 */
data class PowerNeed(
    val date: java.time.LocalDate,
    val tempMaxC: Double,
    val coolingMultiplier: Double,
    val dayWh: Double,         // consommation de jour (06h-22h)
    val nightWh: Double,       // consommation de nuit (22h-06h)
    val totalWh: Double,       // = dayWh + nightWh
    val peakPowerW: Double,    // pic instantané estimé (démarrages clim)
    val byCategory: Map<String, Double>  // Wh par catégorie
) {
    companion object {
        /** Approximation heure → jour/nuit. On compte 16h jour / 8h nuit. */
        const val DAY_HOURS = 16.0
        const val NIGHT_HOURS = 8.0
    }
}

data class PowerProfile(
    val city: String,
    val averageDailyKwh: Double,     // moyenne sur les 3 jours
    val dayKwhAvg: Double,
    val nightKwhAvg: Double,
    val peakPowerW: Double,
    val needs: List<PowerNeed>,
    val totalTwoDaysWh: Double       // énergie pour tenir 48h sans apport
)
