package com.elnourpower.controller

import com.elnourpower.config.currentUserId
import com.elnourpower.entity.UserAppliance
import com.elnourpower.repository.ApplianceCatalog
import com.elnourpower.repository.UserApplianceRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * Inventaire d'appareils sauvegardé par utilisateur.
 * Permet de retrouver ses appareils identifiés à la prochaine connexion.
 */
@RestController
@RequestMapping("/api/my-appliances")
class UserApplianceController(
    private val repo: UserApplianceRepository,
    private val catalog: ApplianceCatalog
) {
    private fun uid(): Long = currentUserId()
        ?: throw ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Non authentifié")

    @GetMapping
    fun list(): List<UserApplianceView> = repo.findByUserId(uid()).map { it.toView(catalog) }

    @PostMapping
    fun add(@RequestBody req: AddRequest): UserApplianceView {
        val u = uid()
        // Si l'appareil existe déjà pour ce user, on met à jour la quantité
        val existing = repo.findByUserIdAndApplianceId(u, req.applianceId)
        val saved = repo.save(
            (existing ?: UserAppliance(userId = u, applianceId = req.applianceId)).apply {
                quantity = req.quantity ?: existing?.quantity ?: 1
                overridePowerWatts = req.overridePowerWatts ?: existing?.overridePowerWatts
                hoursPerDay = req.hoursPerDay ?: existing?.hoursPerDay
            }
        )
        return saved.toView(catalog)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody req: AddRequest): ResponseEntity<UserApplianceView> {
        val u = uid()
        val existing = repo.findByIdAndUserId(id, u) ?: return ResponseEntity.notFound().build()
        req.quantity?.let { existing.quantity = it }
        req.overridePowerWatts?.let { existing.overridePowerWatts = it }
        req.hoursPerDay?.let { existing.hoursPerDay = it }
        return ResponseEntity.ok(repo.save(existing).toView(catalog))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        val u = uid()
        if (repo.findByIdAndUserId(id, u) == null) return ResponseEntity.notFound().build()
        repo.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    /** Supprime tout l'inventaire du user (pour repartir à zéro). */
    @DeleteMapping
    fun clear(): ResponseEntity<Void> {
        val u = uid()
        repo.findByUserId(u).forEach { repo.deleteById(it.id!!) }
        return ResponseEntity.noContent().build()
    }

    data class AddRequest(
        val applianceId: String,
        val quantity: Int? = null,
        val overridePowerWatts: Double? = null,
        val hoursPerDay: Double? = null
    )

    /** Vue enrichie avec les infos du catalogue (nom, catégorie, puissance par défaut). */
    data class UserApplianceView(
        val id: Long?,
        val applianceId: String,
        val name: String,
        val category: String,
        val catalogPowerWatts: Double,
        val quantity: Int,
        val overridePowerWatts: Double?,
        val hoursPerDay: Double?,
        val effectivePowerWatts: Double
    )

    private fun UserAppliance.toView(catalog: ApplianceCatalog): UserApplianceView {
        val base = catalog.byId(applianceId)
        return UserApplianceView(
            id = id,
            applianceId = applianceId,
            name = base?.name ?: applianceId,
            category = base?.category?.name ?: "OTHER",
            catalogPowerWatts = base?.powerWatts ?: 0.0,
            quantity = quantity,
            overridePowerWatts = overridePowerWatts,
            hoursPerDay = hoursPerDay,
            effectivePowerWatts = (overridePowerWatts ?: base?.powerWatts ?: 0.0) * quantity
        )
    }
}
