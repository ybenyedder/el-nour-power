package com.elnourpower.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime

@Entity
@Table(name = "appointments")
class Appointment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false) var titre: String = "",

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    var client: Client? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "partner_id")
    var partner: Partner? = null,

    @Column(nullable = false) var dateTime: LocalDateTime = LocalDateTime.now(),
    var type: String = "VISITE",   // VISITE | DEVIS | INSTALLATION | SUIVI
    var statut: String = "PLANIFIE", // PLANIFIE | CONFIRME | TERMINE | ANNULE
    @Column(length = 2000) var notes: String = "",

    var userId: Long? = null,

    var createdAt: Instant = Instant.now()
) {
    constructor() : this(titre = "")
}
