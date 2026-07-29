package com.elnourpower.model

/**
 * Lecture simulée d'une prise connectée (type Tapo / Tuya / Shelly).
 * En production: brancher l'API cloud du fabricant. Ici: données mock réalistes.
 */
data class SmartPlug(
    val id: String,
    val name: String,
    val applianceId: String?,        // relie à un Appliance si reconnu
    val currentWatts: Double,
    val todayKwh: Double,
    val isOn: Boolean,
    val lastSeenIso: String
)
