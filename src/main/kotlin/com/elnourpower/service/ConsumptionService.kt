package com.elnourpower.service

import com.elnourpower.model.*
import org.springframework.stereotype.Service

/**
 * Calcule les besoins de puissance jour/nuit à partir des appareils, de la
 * météo 3 jours et (option) des prises connectées.
 *
 * Logique:
 *  - chaque appareil a une consommation de base (W × heures × duty cycle)
 *  - les appareils COOLING (clim) subissent un boost thermique = coolingMultiplier
 *    (fonction du Tmax du jour)
 *  - séparation jour/nuit selon isNightOnly et heures d'utilisation
 *  - pic de puissance = somme des puissances plaquées × surgeFactor, avec
 *    diversité de démarrage (0.6) car tout ne démarre pas en même temps
 */
@Service
class ConsumptionService {

    /**
     * Construit la liste d'Appliance effective à partir de sélections précises.
     * Applique l'override de puissance (vraie valeur identifiée sur les marketplaces)
     * et duplique selon la quantité.
     */
    fun expandSelections(
        base: Appliance,
        quantity: Int,
        overridePowerWatts: Double?,
        hoursPerDay: Double?
    ): List<Appliance> = (1..quantity.coerceAtLeast(1)).map {
        base.copy(
            id = "${base.id}#$it",
            powerWatts = overridePowerWatts ?: base.powerWatts,
            dailyHours = hoursPerDay ?: base.dailyHours
        )
    }

    fun compute(
        appliances: List<Appliance>,
        weather: WeatherForecast,
        plugs: List<SmartPlug> = emptyList()
    ): PowerProfile {
        val needs = weather.days.map { day -> needForDay(appliances, day, plugs) }

        val avgTotal = needs.map { it.totalWh }.average()
        val avgDay = needs.map { it.dayWh }.average()
        val avgNight = needs.map { it.nightWh }.average()
        val peak = needs.maxOfOrNull { it.peakPowerW } ?: 0.0
        val totalTwoDays = needs.take(2).sumOf { it.totalWh }

        return PowerProfile(
            city = weather.city,
            averageDailyKwh = round1(avgTotal / 1000.0),
            dayKwhAvg = round1(avgDay / 1000.0),
            nightKwhAvg = round1(avgNight / 1000.0),
            peakPowerW = round0(peak),
            needs = needs,
            totalTwoDaysWh = round0(totalTwoDays)
        )
    }

    private fun needForDay(
        appliances: List<Appliance>,
        day: DayWeather,
        plugs: List<SmartPlug>
    ): PowerNeed {
        val mult = day.coolingMultiplier
        val byCat = mutableMapOf<String, Double>()
        var dayWh = 0.0
        var nightWh = 0.0
        var peak = 0.0

        // Empreinte des prises connectées si on en utilise (remplace l'estimation
        // pour les appareils reliés — données mesurées > estimation).
        val measuredById = plugs.filter { it.applianceId != null }
            .associateBy { it.applianceId!! }
            .mapValues { it.value.todayKwh * 1000.0 } // kWh -> Wh

        for (a in appliances) {
            val measured = measuredById[a.id]
            val totalWh: Double = if (measured != null) {
                measured
            } else {
                val boost = if (a.isCooling) mult else 1.0
                a.effectiveDailyWh * boost
            }

            // Répartition jour/nuit
            val dayPart = when {
                a.isNightOnly -> 0.25          // frigo/allumé la nuit mais tourne aussi le jour
                a.dailyHours <= 0 -> 0.0
                else -> (a.dailyHours.coerceAtMost(PowerNeed.DAY_HOURS)) / a.dailyHours
            }
            val d = totalWh * dayPart
            val n = totalWh - d
            dayWh += d
            nightWh += n

            byCat.merge(a.category.name, totalWh) { x, y -> x + y }

            // Pic: puissance plaquée × surge × boost thermique
            peak += a.powerWatts * a.surgeFactor * (if (a.isCooling) mult else 1.0)
        }

        return PowerNeed(
            date = day.date,
            tempMaxC = day.tempMaxC,
            coolingMultiplier = round2(mult),
            dayWh = round0(dayWh),
            nightWh = round0(nightWh),
            totalWh = round0(dayWh + nightWh),
            peakPowerW = round0(peak * 0.6), // facteur de diversité
            byCategory = byCat.mapValues { round0(it.value) }
        )
    }

    private fun round0(v: Double) = Math.round(v).toDouble()
    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0
    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
}
