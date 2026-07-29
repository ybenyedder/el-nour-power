package com.elnourpower.model

/**
 * Requête entrante du client: ses appareils, sa ville, ses prises connectées.
 *
 * Deux modes de sélection:
 *  - legacy : applianceIds (catalogue seul, puissance estimée)
 *  - précis : selections (catalogue + quantité + puissance réelle identifiée)
 * Si selections est fourni, il est utilisé en priorité.
 */
data class EnergyRequest(
    val city: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val outageHours: Int? = null,
    val applianceIds: List<String> = emptyList(),
    val selections: List<ApplianceSelection> = emptyList(),
    val customAppliances: List<Appliance> = emptyList(),
    val useSmartPlugs: Boolean = false,
    val preferRent: Boolean = false,
    /** Si true, utilise l'inventaire sauvegardé de l'utilisateur connecté. */
    val useSavedInventory: Boolean = false
)

/**
 * Sélection d'un appareil du catalogue avec une identification précise.
 * @param overridePowerWatts puissance réelle trouvée sur Alibaba/AliExpress/STEG
 *        (si null, on utilise la valeur catalogue).
 */
data class ApplianceSelection(
    val applianceId: String,
    val quantity: Int = 1,
    val overridePowerWatts: Double? = null,
    val hoursPerDay: Double? = null
)
