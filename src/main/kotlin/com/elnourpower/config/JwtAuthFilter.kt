package com.elnourpower.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Extrait le JWT du header Authorization: Bearer <token>, le valide,
 * et peuple le SecurityContext avec le userId (principal) et le role.
 * Expose le userId courant via SecurityContextHolder pour les contrôleurs.
 */
@Component
class JwtAuthFilter(private val jwtUtil: JwtUtil) : OncePerRequestFilter() {

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain
    ) {
        val header = req.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ").trim()
            val claims = jwtUtil.parse(token)
            if (claims != null) {
                val userId = claims.subject.toLongOrNull()
                val role = claims["role"] as? String ?: "USER"
                if (userId != null) {
                    val auth = UsernamePasswordAuthenticationToken(
                        userId, null, listOf(SimpleGrantedAuthority("ROLE_$role"))
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }
        chain.doFilter(req, res)
    }
}

/** Accès au userId courant depuis n'importe où ( null si non authentifié ). */
fun currentUserId(): Long? =
    SecurityContextHolder.getContext()?.authentication?.principal as? Long
