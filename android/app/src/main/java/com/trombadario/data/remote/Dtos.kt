package com.trombadario.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val app: String,
    @SerialName("server_id") val serverId: String,
    // Default false so an older server (before the setup wizard existed) still
    // parses instead of failing the whole health check.
    @SerialName("setup_required") val setupRequired: Boolean = false,
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    @SerialName("is_active") val isActive: Boolean,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_CHILD = "child"
    }
}

@Serializable
data class UserCreateDto(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
)

@Serializable
data class UserUpdateDto(
    @SerialName("display_name") val displayName: String? = null,
    val password: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class TrombadiceDto(
    val id: Int,
    val title: String,
    val description: String,
    // ISO-8601 with an explicit offset, always UTC - the backend guarantees it
    // (see backend/app/types.py). Parsed with Instant, rendered in local time.
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("child_id") val childId: Int,
    @SerialName("author_id") val authorId: Int,
    /** The task that wasn't done, when this trombadice is about one. */
    @SerialName("task_id") val taskId: Int? = null,
)

@Serializable
data class TrombadiceCreateDto(
    val title: String,
    val description: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("child_id") val childId: Int,
    @SerialName("task_id") val taskId: Int? = null,
)

@Serializable
data class TrombadiceUpdateDto(
    val title: String? = null,
    val description: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("task_id") val taskId: Int? = null,
)

@Serializable
data class ApiErrorDto(val detail: String? = null)

// --------------------------------------------------------------------------
// Tarefas
// --------------------------------------------------------------------------

@Serializable
data class TaskDto(
    val id: Int,
    val name: String,
    val description: String,
    val periodicity: String,
    /** Python weekday numbers, 0 = Monday. */
    val weekdays: List<Int> = emptyList(),
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    @SerialName("child_id") val childId: Int,
    @SerialName("is_active") val isActive: Boolean,
) {
    companion object {
        const val DAILY = "daily"
        const val WEEKLY = "weekly"
        const val MONTHLY = "monthly"
        const val ONCE = "once"
    }
}

@Serializable
data class TaskCreateDto(
    val name: String,
    val description: String = "",
    val periodicity: String = TaskDto.DAILY,
    val weekdays: List<Int> = emptyList(),
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    @SerialName("child_id") val childId: Int,
)

@Serializable
data class TaskUpdateDto(
    val name: String? = null,
    val description: String? = null,
    val periodicity: String? = null,
    val weekdays: List<Int>? = null,
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

// --------------------------------------------------------------------------
// Castigos
// --------------------------------------------------------------------------

@Serializable
data class PunishmentDto(
    val id: Int,
    val reason: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("ended_early_at") val endedEarlyAt: String? = null,
    @SerialName("child_id") val childId: Int,
    @SerialName("trombadice_ids") val trombadiceIds: List<Int> = emptyList(),
    // Computed server-side: the phone's clock is not the authority on whether
    // someone is grounded.
    @SerialName("is_active") val isActive: Boolean = false,
)

@Serializable
data class PunishmentCreateDto(
    @SerialName("child_id") val childId: Int,
    @SerialName("ends_at") val endsAt: String,
    val reason: String = "",
    @SerialName("trombadice_ids") val trombadiceIds: List<Int> = emptyList(),
)

@Serializable
data class PunishmentUpdateDto(
    val reason: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("trombadice_ids") val trombadiceIds: List<Int>? = null,
    @SerialName("end_now") val endNow: Boolean? = null,
)
