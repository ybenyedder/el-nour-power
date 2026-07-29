package com.elnourpower.model

/**
 * Un appareil domestique: climatiseur, four, machine à laver, frigo, TV, etc.
 * Tous les watts sont en puissance " utile " — le moteur d'estimation applique
 * un facteur de marche (duty cycle) réaliste et un boost thermique estival.
 */
data class Appliance(
    val id: String,
    val name: String,
    val category: ApplianceCategory,
    val powerWatts: Double,            // puissance plaquée
    val dutyCycle: Double,             // 0..1 part du temps réellement actif
    val dailyHours: Double,            // heures d'utilisation par jour
    val isNightOnly: Boolean = false,  // utilisé la nuit (éclairage, frigo...)
    val isCooling: Boolean = false,    // sensible à la chaleur (clim, frigo)
    val surgeFactor: Double = 1.0      // pic de démarrage (clim ~3x)
) {
    val effectiveDailyWh: Double
        get() = powerWatts * dailyHours * dutyCycle
}

enum class ApplianceCategory {
    COOLING,    // climatiseur
    HEATING,    // four, radiateur
    LAUNDRY,    // machine à laver, sèche-linge
    COLD,       // frigo, congélateur
    COOKING,    // plaque, micro-onde
    LIGHTING,   // éclairage
    ELECTRONICS,// TV, ordi, box
    WATER,      // chauffe-eau, pompe
    OTHER
}
