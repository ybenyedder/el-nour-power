package com.elnourpower.repository

import com.elnourpower.entity.Client
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClientRepository : JpaRepository<Client, Long> {
    fun findByUserId(userId: Long): List<Client>
    fun findByIdAndUserId(id: Long, userId: Long): Client?
}
