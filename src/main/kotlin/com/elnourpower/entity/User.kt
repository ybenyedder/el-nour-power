package com.elnourpower.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["email"])]
)
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true) var email: String = "",
    @Column(nullable = false) var nom: String = "",
    @Column(nullable = false, name = "password_hash") var passwordHash: String = "",
    var role: String = "USER",

    var createdAt: Instant = Instant.now()
) {
    constructor() : this(email = "")
}
