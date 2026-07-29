package com.elnourpower.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import javax.crypto.SecretKey

/**
 * Génération et validation des JWT.
 * Le subject contient le userId ; un claim "email" et "role" sont ajoutés.
 */
@Component
class JwtUtil(
    @Value("\${app.jwt.secret}") secretB64: String,
    @Value("\${app.jwt.expiration-hours:72}") private val expirationHours: Long
) {
    private val key: SecretKey = run {
        // Accepte soit un base64, soit une chaîne brute ; on décode si base64 valide.
        val bytes = try {
            Base64.getDecoder().decode(secretB64)
        } catch (_: IllegalArgumentException) {
            secretB64.toByteArray(StandardCharsets.UTF_8)
        }
        // Padding à 256 bits minimum requis par HS256.
        val padded = if (bytes.size < 32) bytes.copyOf(32) else bytes
        Keys.hmacShaKeyFor(padded)
    }

    fun generate(userId: Long, email: String, role: String): String {
        val now = java.util.Date()
        val exp = java.util.Date(now.time + Duration.ofHours(expirationHours).toMillis())
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .issuedAt(now)
            .expiration(exp)
            .signWith(key)
            .compact()
    }

    fun parse(token: String): Claims? = try {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    } catch (_: Exception) {
        null
    }

    fun userId(token: String): Long? = parse(token)?.subject?.toLongOrNull()
}
