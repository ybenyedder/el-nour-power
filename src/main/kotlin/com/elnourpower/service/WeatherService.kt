package com.elnourpower.service

import com.elnourpower.config.AppProperties
import com.elnourpower.model.DayWeather
import com.elnourpower.model.WeatherForecast
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDate

/**
 * Récupère la météo 3 jours via l'API publique open-meteo (gratuite, pas de clé).
 * Géocodage du nom de ville inclus. Fallback sur valeur par défaut si échec.
 */
@Service
class WeatherService(
    private val rest: RestTemplate,
    private val props: AppProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun forecast(city: String?): WeatherForecast {
        val (lat, lon, name) = resolve(city)
        return fetch(lat, lon, name)
    }

    private fun resolve(city: String?): Triple<Double, Double, String> {
        val asked = city?.takeIf { it.isNotBlank() } ?: props.defaultCity
        return try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${asked.encode()}&count=1&language=fr&format=json"
            val node = rest.getForObject(url, JsonNode::class.java)
            val r = node?.path("results")?.firstOrNull()
            if (r != null) {
                Triple(r.path("latitude").asDouble(), r.path("longitude").asDouble(), r.path("name").asText())
            } else {
                Triple(props.defaultLat, props.defaultLon, props.defaultCity)
            }
        } catch (e: Exception) {
            log.warn("Géocodage échoué pour '{}': {}", asked, e.message)
            Triple(props.defaultLat, props.defaultLon, props.defaultCity)
        }
    }

    private fun fetch(lat: Double, lon: Double, city: String): WeatherForecast {
        val url = (
            "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&daily=temperature_2m_max,temperature_2m_min,temperature_2m_mean,sunshine_duration,precipitation_sum,wind_speed_10m_max,uv_index_max" +
            "&timezone=auto&forecast_days=3"
        )
        return try {
            val node = rest.getForObject(url, JsonNode::class.java)!!
            val tz = node.path("timezone").asText("Africa/Tunis")
            val d = node.path("daily")
            val dates = d.path("time").map { LocalDate.parse(it.asText()) }
            val tmax = d.path("temperature_2m_max").map { it.asDouble() }
            val tmin = d.path("temperature_2m_min").map { it.asDouble() }
            val tmean = d.path("temperature_2m_mean").map { it.asDouble() }
            val sun = d.path("sunshine_duration").map { it.asDouble() / 3600.0 } // s -> h
            val prec = d.path("precipitation_sum").map { it.asDouble() }
            val wind = d.path("wind_speed_10m_max").map { it.asDouble() }
            val uv = d.path("uv_index_max").map { it.asDouble() }

            val days = dates.indices.map { i ->
                DayWeather(
                    date = dates[i],
                    tempMaxC = tmax.getOrElse(i) { 28.0 },
                    tempMinC = tmin.getOrElse(i) { 18.0 },
                    tempMeanC = tmean.getOrElse(i) { 23.0 },
                    sunshineHours = sun.getOrElse(i) { 6.0 },
                    precipitationMm = prec.getOrElse(i) { 0.0 },
                    windKmh = wind.getOrElse(i) { 15.0 },
                    uvIndex = uv.getOrElse(i) { 6.0 }
                )
            }
            WeatherForecast(city, lat, lon, tz, days)
        } catch (e: Exception) {
            log.error("Météo indisponible pour ({},{}): {}", lat, lon, e.message)
            WeatherForecast(city, lat, lon, "Africa/Tunis", fallback3Days())
        }
    }

    private fun fallback3Days(): List<DayWeather> =
        (0..2).map { i ->
            DayWeather(
                date = LocalDate.now().plusDays(i.toLong()),
                tempMaxC = 32.0, tempMinC = 22.0, tempMeanC = 27.0,
                sunshineHours = 7.0, precipitationMm = 0.0, windKmh = 15.0, uvIndex = 8.0
            )
        }

    private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8")
}
