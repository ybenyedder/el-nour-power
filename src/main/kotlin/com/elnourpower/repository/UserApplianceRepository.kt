package com.elnourpower.repository

import com.elnourpower.entity.UserAppliance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserApplianceRepository : JpaRepository<UserAppliance, Long> {
    fun findByUserId(userId: Long): List<UserAppliance>
    fun findByIdAndUserId(id: Long, userId: Long): UserAppliance?
    fun findByUserIdAndApplianceId(userId: Long, applianceId: String): UserAppliance?
}
