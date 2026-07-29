package com.elnourpower.repository

import com.elnourpower.entity.Partner
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PartnerRepository : JpaRepository<Partner, Long> {
    fun findByUserId(userId: Long): List<Partner>
    fun findByIdAndUserId(id: Long, userId: Long): Partner?
}
