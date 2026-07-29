package com.elnourpower.model

/**
 * Source d'énergie de secours: groupe électrogène gaz ou kit solaire.
 * Soit à l'achat, soit en location. Prix en dinars tunisiens.
 */
data class PowerSource(
    val id: String,
    val kind: PowerSourceKind,
    val name: String,
    val powerKw: Double,             // puissance de sortie
    val fuelType: String,            // gaz, essence, soleil...
    val runtimeHours: Int,           // autonomie sur un plein (géné) / 0 = infini (solaire)
    val purchaseTnd: Double,
    val monthlyRentTnd: Double,      // 0 si non louable
    val maintenancePerYearTnd: Double,
    val co2PerKgKwh: Double,         // émissions kg CO2 / kWh
    val notes: String,
    val sourceUrl: String
)

enum class PowerSourceKind { GAS_GENERATOR, SOLAR_KIT, HYBRID }
