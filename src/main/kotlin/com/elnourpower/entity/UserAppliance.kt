package com.elnourpower.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Appareil enregistré dans l'inventaire d'un utilisateur.
 * - applianceId référence le catalogue (nom, catégorie, duty cycle, heures/jour par défaut)
 * - quantity : combien l'utilisateur en possède
 * - overridePowerWatts : si l'utilisateur a identifié la VRAIE puissance sur
 *   Alibaba/AliExpress/STEG, il la saisit ici → le calcul l'utilise à la place
 *   de la valeur catalogue (calcul précis).
 */
@Entity
@Table(name = "user_appliances")
class UserAppliance(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false) var userId: Long = 0,
    @Column(nullable = false) var applianceId: String = "",

    var quantity: Int = 1,
    var overridePowerWatts: Double? = null,
    var hoursPerDay: Double? = null,

    var createdAt: Instant = Instant.now()
) {
    constructor() : this(applianceId = "")
}
