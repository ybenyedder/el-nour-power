package com.elnourpower.model

/**
 * Batterie de stockage domestique.
 * Prix & capacités issus du marché 2026 (Tesla Powerwall 3, Pylontech,
 * Huawei LUNA 2000, Enphase IQ Battery 5P, Zendure AB2000S, BYD).
 */
data class Battery(
    val id: String,
    val brand: String,
    val model: String,
    val chemistry: String,           // LiFePO4, NMC...
    val usableKwh: Double,           // capacité utile (après DoD)
    val continuousPowerKw: Double,   // puissance de sortie continue
    val cycles: Int,                 // cycles à 80% DoD
    val priceTnd: Double,            // prix indicatif en dinars tunisiens
    val warrantyYears: Int,
    val sourceUrl: String
)
