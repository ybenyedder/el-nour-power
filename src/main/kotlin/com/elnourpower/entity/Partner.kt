package com.elnourpower.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "partners")
class Partner(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false) var nom: String = "",
    var type: String = "INSTALLATEUR", // INSTALLATEUR | FOURNISSEUR | DISTRIBUTEUR
    var telephone: String = "",
    var email: String = "",
    var zone: String = "",
    @Column(length = 2000) var notes: String = "",

    var userId: Long? = null,

    var createdAt: Instant = Instant.now()
) {
    constructor() : this(nom = "")
}
