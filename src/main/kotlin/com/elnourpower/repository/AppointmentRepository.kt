package com.elnourpower.repository

import com.elnourpower.entity.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AppointmentRepository : JpaRepository<Appointment, Long> {
    fun findByUserId(userId: Long): List<Appointment>
    fun findByIdAndUserId(id: Long, userId: Long): Appointment?

    /** Prochains RDV à partir d'une date, triés chronologiquement, pour un user. */
    fun findByUserIdAndDateTimeAfterAndStatutNotOrderByDateTimeAsc(
        userId: Long, from: LocalDateTime, statut: String
    ): List<Appointment>
}
