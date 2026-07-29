package com.elnourpower.service

import com.elnourpower.model.SmartPlug
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.random.Random

private fun ClosedFloatingPointRange<Double>.rand(): Double =
    Random.nextDouble(start, endInclusive)

private fun IntRange.rand(): Int =
    Random.nextInt(first, last + 1)

/**
 * Service de prises connectées. Stub réaliste qui simule une lecture live
 * (style Tapo/Tuya/Shelly). À brancher sur l'API cloud du fabricant en prod.
 *
 * Pour la démo: 4 prises réparties sur frigo, box, TV, ordinateur — valeurs
 * plausible avec un peu de bruit pour que ça ressemble à du live.
 */
@Service
class SmartPlugService {

    private val base = listOf(
        Triple("plug_01", "Prise Frigo", "frigo_a"),
        Triple("plug_02", "Prise Box internet", "box_internet"),
        Triple("plug_03", "Prise TV salon", "tv_led"),
        Triple("plug_04", "Prise Bureau (PC)", "ordinateur")
    )

    fun readAll(): List<SmartPlug> = base.map { (id, name, appId) ->
        val noise = (0.85..1.15).rand()
        val watts = baseWatts(appId) * noise
        val kwh = watts / 1000.0 * (1..8).rand()
        SmartPlug(
            id = id,
            name = name,
            applianceId = appId,
            currentWatts = Math.round(watts * 10.0) / 10.0,
            todayKwh = Math.round(kwh * 100.0) / 100.0,
            isOn = true,
            lastSeenIso = Instant.now().toString()
        )
    }

    private fun baseWatts(appId: String): Double = when (appId) {
        "frigo_a" -> 140.0
        "box_internet" -> 12.0
        "tv_led" -> 95.0
        "ordinateur" -> 180.0
        else -> 50.0
    }
}
