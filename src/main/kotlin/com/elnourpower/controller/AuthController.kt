package com.elnourpower.controller

import com.elnourpower.config.currentUserId
import com.elnourpower.service.UserService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val users: UserService) {

    data class RegisterRequest(val email: String, val nom: String?, val password: String)
    data class LoginRequest(val email: String, val password: String)

    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest) =
        users.register(req.email, req.nom ?: "", req.password)

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest) =
        users.login(req.email, req.password)

    /** Profil de l'utilisateur connecté. */
    @GetMapping("/me")
    fun me(): Map<String, Any?> {
        val id = currentUserId() ?: throw IllegalStateException("Non authentifié")
        val u = users.getById(id)
        return mapOf(
            "userId" to u.id,
            "email" to u.email,
            "nom" to u.nom,
            "role" to u.role
        )
    }
}
