package com.elnourpower.service

import com.elnourpower.config.JwtUtil
import com.elnourpower.entity.User
import com.elnourpower.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    private val jwtUtil: JwtUtil
) {
    fun register(email: String, nom: String, rawPassword: String): AuthResult {
        if (email.isBlank() || rawPassword.length < 6) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email requis et mot de passe ≥ 6 caractères.")
        }
        if (users.existsByEmail(email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email.")
        }
        val user = User(
            email = email.trim(),
            nom = nom.trim().ifBlank { email.substringBefore('@') },
            passwordHash = encoder.encode(rawPassword),
            role = "USER"
        )
        val saved = users.save(user)
        return issue(saved)
    }

    fun login(email: String, rawPassword: String): AuthResult {
        val user = users.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides.")
        if (!encoder.matches(rawPassword, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides.")
        }
        return issue(user)
    }

    fun getById(id: Long): User =
        users.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable.")
        }

    private fun issue(user: User): AuthResult {
        val uid = user.id!!
        val token = jwtUtil.generate(uid, user.email, user.role)
        return AuthResult(
            token = token,
            userId = uid,
            email = user.email,
            nom = user.nom,
            role = user.role
        )
    }

    data class AuthResult(
        val token: String,
        val userId: Long,
        val email: String,
        val nom: String,
        val role: String
    )
}
