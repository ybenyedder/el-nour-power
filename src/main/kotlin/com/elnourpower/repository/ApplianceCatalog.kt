package com.elnourpower.repository

import com.elnourpower.model.Appliance
import com.elnourpower.model.ApplianceCategory.*
import org.springframework.stereotype.Component

/**
 * Catalogue d'appareils domestiques typiques (Tunisie / Méditerranée).
 * Watts plaqués, facteur de marche (duty cycle), heures/jour réalistes.
 */
@Component
class ApplianceCatalog {

    private val all: List<Appliance> = listOf(
        Appliance("clim_12000", "Climatiseur 12000 BTU", COOLING, 1200.0, 0.65, 8.0, isCooling = true, surgeFactor = 3.0),
        Appliance("clim_18000", "Climatiseur 18000 BTU", COOLING, 1800.0, 0.65, 8.0, isCooling = true, surgeFactor = 3.0),
        Appliance("clim_24000", "Climatiseur 24000 BTU", COOLING, 2400.0, 0.65, 8.0, isCooling = true, surgeFactor = 3.0),
        Appliance("frigo_a", "Réfrigérateur A+", COLD, 150.0, 0.35, 24.0, isNightOnly = true, isCooling = true),
        Appliance("frigo_old", "Réfrigérateur ancien", COLD, 300.0, 0.40, 24.0, isNightOnly = true, isCooling = true),
        Appliance("congelateur", "Congélateur", COLD, 200.0, 0.40, 24.0, isNightOnly = true, isCooling = true),
        Appliance("lave_linge", "Machine à laver", LAUNDRY, 2000.0, 0.45, 1.5),
        Appliance("lave_vaisselle", "Lave-vaisselle", LAUNDRY, 1800.0, 0.40, 1.0),
        Appliance("four_elec", "Four électrique", COOKING, 2200.0, 0.55, 0.8),
        Appliance("micro_onde", "Micro-ondes", COOKING, 1100.0, 0.90, 0.3),
        Appliance("plaque_induc", "Plaque induction", COOKING, 2000.0, 0.50, 1.0),
        Appliance("chauffe_eau", "Chauffe-eau 100L", WATER, 2000.0, 0.30, 2.0),
        Appliance("tv_led", "TV LED 55\"", ELECTRONICS, 110.0, 0.95, 5.0, isNightOnly = true),
        Appliance("box_internet", "Box internet / routeur", ELECTRONICS, 15.0, 1.0, 24.0, isNightOnly = true),
        Appliance("ordinateur", "Ordinateur de bureau", ELECTRONICS, 200.0, 0.70, 6.0),
        Appliance("console", "Console de jeu", ELECTRONICS, 150.0, 0.80, 2.0, isNightOnly = true),
        Appliance("eclairage_led", "Éclairage LED maison", LIGHTING, 60.0, 0.60, 6.0, isNightOnly = true),
        Appliance("eclairage_halog", "Éclairage halogène", LIGHTING, 250.0, 0.60, 6.0, isNightOnly = true),
        Appliance("pompe_eau", "Surpresseur d'eau", WATER, 750.0, 0.30, 1.5),
        Appliance("ventilateur", "Ventilateur plafond", COOLING, 75.0, 0.90, 8.0, isCooling = true),
        Appliance("fer_repasser", "Fer à repasser", HEATING, 1800.0, 0.50, 0.4),
        Appliance("aspirateur", "Aspirateur", OTHER, 1400.0, 0.60, 0.3)
    )

    fun all(): List<Appliance> = all
    fun defaultHome(): List<Appliance> = listOf(
        "clim_18000", "frigo_a", "lave_linge", "four_elec",
        "tv_led", "box_internet", "eclairage_led", "chauffe_eau", "ordinateur"
    ).mapNotNull { id -> all.find { it.id == id } }

    fun byIds(ids: List<String>): List<Appliance> =
        all.filter { it.id in ids }

    fun byId(id: String): Appliance? = all.find { it.id == id }
}
