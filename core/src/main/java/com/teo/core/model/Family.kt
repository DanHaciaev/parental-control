package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class CommandType { RELEASE_PROTECTION, LOCK_NOW, REFRESH_STATUS }
enum class CommandStatus { PENDING, DONE }

data class PendingCommand(
    val type: CommandType = CommandType.REFRESH_STATUS,
    @ServerTimestamp val requestedAt: Date? = null,
    val status: CommandStatus = CommandStatus.PENDING
)

data class Family(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val parentEmail: String = "",
    val parentPhone: String? = null,
    val childUid: String? = null,
    val childDeviceName: String? = null,
    val timezone: String = "Europe/Moscow",
    val protectionPinHash: String = "",
    val protectionPinSalt: String = "",
    /** Minutes since midnight; both null = bedtime disabled. */
    val bedtimeStartMinutes: Int? = null,
    val bedtimeEndMinutes: Int? = null,
    @ServerTimestamp val createdAt: Date? = null,
    val pendingCommand: PendingCommand? = null
)
