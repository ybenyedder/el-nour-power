package com.elnourpower.model

import java.time.LocalDate

data class WeatherForecast(
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val days: List<DayWeather>
)

data class DayWeather(
    val date: LocalDate,
    val tempMaxC: Double,
    val tempMinC: Double,
    val tempMeanC: Double,
    val sunshineHours: Double,    // heures d'ensoleillement utiles au solaire
    val precipitationMm: Double,
    val windKmh: Double,
    val uvIndex: Double
) {
    /** Facteur thermique appliqué aux climatiseurs: +1 par °C au-dessus de 24°C. */
    val coolingMultiplier: Double
        get() = ((tempMaxC - 24.0).coerceAtLeast(0.0) / 10.0) + 1.0
}
