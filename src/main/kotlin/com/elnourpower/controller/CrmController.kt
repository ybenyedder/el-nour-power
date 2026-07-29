package com.elnourpower.controller

import com.elnourpower.config.currentUserId
import com.elnourpower.entity.Appointment
import com.elnourpower.entity.Client
import com.elnourpower.entity.Partner
import com.elnourpower.repository.AppointmentRepository
import com.elnourpower.repository.ClientRepository
import com.elnourpower.repository.PartnerRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * CRUD Clients / Partenaires / Rendez-vous — TOUS scopés à l'utilisateur connecté.
 * Un utilisateur ne voit et ne modifie que SES données.
 */
@RestController
@RequestMapping("/api")
class CrmController(
    private val clients: ClientRepository,
    private val partners: PartnerRepository,
    private val appointments: AppointmentRepository
) {

    private fun uid(): Long = currentUserId()
        ?: throw ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Non authentifié")

    // ===== Clients =====
    @GetMapping("/clients")
    fun listClients(): List<Client> = clients.findByUserId(uid()).sortedByDescending { it.createdAt }

    @PostMapping("/clients")
    fun createClient(@RequestBody c: Client): Client {
        c.userId = uid()
        return clients.save(c)
    }

    @PutMapping("/clients/{id}")
    fun updateClient(@PathVariable id: Long, @RequestBody c: Client): ResponseEntity<Client> {
        val existing = clients.findByIdAndUserId(id, uid()) ?: return ResponseEntity.notFound().build()
        existing.nom = c.nom; existing.prenom = c.prenom; existing.telephone = c.telephone
        existing.email = c.email; existing.adresse = c.adresse; existing.ville = c.ville; existing.notes = c.notes
        return ResponseEntity.ok(clients.save(existing))
    }

    @DeleteMapping("/clients/{id}")
    fun deleteClient(@PathVariable id: Long): ResponseEntity<Void> {
        if (clients.findByIdAndUserId(id, uid()) == null) return ResponseEntity.notFound().build()
        clients.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    // ===== Partners =====
    @GetMapping("/partners")
    fun listPartners(): List<Partner> = partners.findByUserId(uid()).sortedByDescending { it.createdAt }

    @PostMapping("/partners")
    fun createPartner(@RequestBody p: Partner): Partner {
        p.userId = uid()
        return partners.save(p)
    }

    @PutMapping("/partners/{id}")
    fun updatePartner(@PathVariable id: Long, @RequestBody p: Partner): ResponseEntity<Partner> {
        val existing = partners.findByIdAndUserId(id, uid()) ?: return ResponseEntity.notFound().build()
        existing.nom = p.nom; existing.type = p.type; existing.telephone = p.telephone
        existing.email = p.email; existing.zone = p.zone; existing.notes = p.notes
        return ResponseEntity.ok(partners.save(existing))
    }

    @DeleteMapping("/partners/{id}")
    fun deletePartner(@PathVariable id: Long): ResponseEntity<Void> {
        if (partners.findByIdAndUserId(id, uid()) == null) return ResponseEntity.notFound().build()
        partners.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    // ===== Appointments =====
    @GetMapping("/appointments")
    fun listAppointments(): List<Appointment> =
        appointments.findByUserId(uid()).sortedBy { it.dateTime }

    @GetMapping("/appointments/upcoming")
    fun upcoming(): List<Appointment> =
        appointments.findByUserIdAndDateTimeAfterAndStatutNotOrderByDateTimeAsc(uid(), LocalDateTime.now(), "ANNULE")
            .take(20)

    @PostMapping("/appointments")
    fun createAppointment(@RequestBody a: AppointmentRequest): Appointment {
        val u = uid()
        val appt = Appointment().apply {
            titre = a.titre
            dateTime = LocalDateTime.parse(a.dateTime)
            type = a.type ?: "VISITE"
            statut = a.statut ?: "PLANIFIE"
            notes = a.notes ?: ""
            userId = u
        }
        a.clientId?.let { cid -> appt.client = clients.findByIdAndUserId(cid, u) }
        a.partnerId?.let { pid -> appt.partner = partners.findByIdAndUserId(pid, u) }
        return appointments.save(appt)
    }

    @PutMapping("/appointments/{id}")
    fun updateAppointment(@PathVariable id: Long, @RequestBody a: AppointmentRequest): ResponseEntity<Appointment> {
        val u = uid()
        val appt = appointments.findByIdAndUserId(id, u) ?: return ResponseEntity.notFound().build()
        appt.titre = a.titre
        a.dateTime?.let { appt.dateTime = LocalDateTime.parse(it) }
        a.type?.let { appt.type = it }
        a.statut?.let { appt.statut = it }
        a.notes?.let { appt.notes = it }
        appt.client = a.clientId?.let { clients.findByIdAndUserId(it, u) }
        appt.partner = a.partnerId?.let { partners.findByIdAndUserId(it, u) }
        return ResponseEntity.ok(appointments.save(appt))
    }

    @DeleteMapping("/appointments/{id}")
    fun deleteAppointment(@PathVariable id: Long): ResponseEntity<Void> {
        if (appointments.findByIdAndUserId(id, uid()) == null) return ResponseEntity.notFound().build()
        appointments.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    data class AppointmentRequest(
        val titre: String = "",
        val clientId: Long? = null,
        val partnerId: Long? = null,
        val dateTime: String? = null,
        val type: String? = null,
        val statut: String? = null,
        val notes: String? = null
    )
}
