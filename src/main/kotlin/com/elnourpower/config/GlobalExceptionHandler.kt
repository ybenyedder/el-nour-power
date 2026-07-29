package com.elnourpower.config

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * Capture les ResponseStatusException avant que le filtre de sécurité ne les
 * convertisse en 403 générique. Garantit que login invalide → 401, email
 * dupliqué → 409, etc.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(ex: ResponseStatusException): ResponseEntity<Map<String, Any>> {
        val body = mapOf(
            "status" to ex.statusCode.value(),
            "error" to (ex.reason ?: "Erreur"),
            "message" to (ex.reason ?: "Erreur")
        )
        return ResponseEntity.status(ex.statusCode).body(body)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadArg(ex: IllegalArgumentException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("status" to 400, "error" to (ex.message ?: "Requête invalide")))

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("status" to 401, "error" to (ex.message ?: "Non authentifié")))
}
