package com.trombadario.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val app: String,
    @SerialName("server_id") val serverId: String,
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
data class EventDto(
    val id: Int,
    val title: String,
    val description: String,
    // ISO-8601 with an explicit offset, always UTC - the backend guarantees it
    // (see backend/app/types.py). Parsed with Instant, rendered in local time.
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("child_id") val childId: Int,
    @SerialName("author_id") val authorId: Int,
)

@Serializable
data class EventCreateDto(
    val title: String,
    val description: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("child_id") val childId: Int,
)

@Serializable
data class EventUpdateDto(
    val title: String? = null,
    val description: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
)

@Serializable
data class ApiErrorDto(val detail: String? = null)
