package com.elnourpower.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "clients")
class Client(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false) var nom: String = "",
    var prenom: String = "",
    var telephone: String = "",
    var email: String = "",
    var adresse: String = "",
    var ville: String = "",
    @Column(length = 2000) var notes: String = "",

    /** Propriétaire de la fiche (compte connecté). Null pour la rétrocompat. */
    var userId: Long? = null,

    var createdAt: Instant = Instant.now()
) {
    /** Constructeur sans-arg requis par JPA (généré par kotlin-jpa). */
    constructor() : this(nom = "")
}
