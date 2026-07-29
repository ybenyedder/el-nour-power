package com.elnourpower.repository

import com.elnourpower.model.Battery
import org.springframework.stereotype.Component

/**
 * Catalogue batteries 2026 — données marché (Tesla, Pylontech, Huawei, Enphase,
 * Zendure, BYD). Prix indicatifs convertis en dinars tunisiens (1 EUR ≈ 3.4 TND).
 * Sources: Selectra, Hellowatt, Haisic Storage, EDF Solutions Solaires (2026).
 */
@Component
class BatteryCatalog {

    private val all: List<Battery> = listOf(
        Battery(
            id = "pylontech_us3000c",
            brand = "Pylontech",
            model = "US3000C",
            chemistry = "LiFePO4",
            usableKwh = 3.55,
            continuousPowerKw = 3.5,
            cycles = 6000,
            priceTnd = 6800.0,
            warrantyYears = 10,
            sourceUrl = "https://selectra.info/energie/solaire/meilleure-batterie"
        ),
        Battery(
            id = "huawei_luna2000_5",
            brand = "Huawei",
            model = "LUNA2000-5",
            chemistry = "Lithium-ion",
            usableKwh = 5.0,
            continuousPowerKw = 2.5,
            cycles = 5000,
            priceTnd = 9200.0,
            warrantyYears = 10,
            sourceUrl = "https://www.hellowatt.fr/panneaux-solaires-photovoltaique/meilleure-batterie"
        ),
        Battery(
            id = "huawei_luna2000_10",
            brand = "Huawei",
            model = "LUNA2000-10",
            chemistry = "Lithium-ion",
            usableKwh = 10.0,
            continuousPowerKw = 5.0,
            cycles = 5000,
            priceTnd = 17000.0,
            warrantyYears = 10,
            sourceUrl = "https://www.hellowatt.fr/panneaux-solaires-photovoltaique/meilleure-batterie"
        ),
        Battery(
            id = "tesla_powerwall3",
            brand = "Tesla",
            model = "Powerwall 3",
            chemistry = "LiFePO4",
            usableKwh = 13.5,
            continuousPowerKw = 11.5,
            cycles = 6000,
            priceTnd = 42000.0,
            warrantyYears = 10,
            sourceUrl = "https://haisicstorage.com/fr/best-home-battery-for-solar/"
        ),
        Battery(
            id = "enphase_iq5p",
            brand = "Enphase",
            model = "IQ Battery 5P",
            chemistry = "LiFePO4",
            usableKwh = 5.0,
            continuousPowerKw = 3.84,
            cycles = 6000,
            priceTnd = 10200.0,
            warrantyYears = 15,
            sourceUrl = "https://selectra.info/energie/solaire/meilleure-batterie"
        ),
        Battery(
            id = "zendure_ab2000s",
            brand = "Zendure",
            model = "AB2000S",
            chemistry = "LiFePO4",
            usableKwh = 2.0,
            continuousPowerKw = 2.4,
            cycles = 6000,
            priceTnd = 4900.0,
            warrantyYears = 5,
            sourceUrl = "https://www.hellowatt.fr/panneaux-solaires-photovoltaique/meilleure-batterie"
        ),
        Battery(
            id = "byd_premium_hvs_7",
            brand = "BYD",
            model = "Battery-Box Premium HVS 7.7",
            chemistry = "LiFePO4",
            usableKwh = 7.7,
            continuousPowerKw = 5.7,
            cycles = 6000,
            priceTnd = 14500.0,
            warrantyYears = 10,
            sourceUrl = "https://www.monkitsolaire.fr/blog/meilleure-batterie-pour-panneau-solaire-n404"
        )
    )

    fun all(): List<Battery> = all
    fun byId(id: String): Battery? = all.find { it.id == id }
    fun cheapest(): Battery = all.minByOrNull { it.priceTnd / it.usableKwh }!!
}
