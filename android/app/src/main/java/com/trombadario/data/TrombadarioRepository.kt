package com.trombadario.data

import com.trombadario.data.remote.ApiProvider
import com.trombadario.data.remote.EventCreateDto
import com.trombadario.data.remote.EventDto
import com.trombadario.data.remote.EventUpdateDto
import com.trombadario.data.remote.TokenDto
import com.trombadario.data.remote.TrombadarioApi
import com.trombadario.data.remote.UserCreateDto
import com.trombadario.data.remote.UserDto
import com.trombadario.data.remote.UserUpdateDto
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Single facade over the API. Every call funnels through [call] so that the two
 * cross-cutting rules live in exactly one place: a network failure means "not at
 * home", and a 401 ends the session.
 */
class TrombadarioRepository(
    private val apiProvider: ApiProvider,
    private val serverConfigStore: ServerConfigStore,
    private val sessionStore: SessionStore,
) {
    /** Set by the app container; invoked whenever the server turns out to be
     *  unreachable, so the gate can flip without waiting for its own poll. */
    var onUnreachable: (() -> Unit)? = null
    var onSessionExpired: (() -> Unit)? = null

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun api(): TrombadarioApi? =
        serverConfigStore.baseUrl.first()?.let { apiProvider.api(it) }

    private suspend fun <T, R> request(
        block: suspend (TrombadarioApi) -> Response<T>,
        body: (Response<T>) -> ApiResult<R>,
    ): ApiResult<R> {
        val api = api() ?: return ApiResult.Unreachable
        return try {
            val response = block(api)
            when {
                response.isSuccessful -> body(response)
                response.code() == 401 -> {
                    sessionStore.clear()
                    onSessionExpired?.invoke()
                    ApiResult.Unauthorized
                }
                response.code() == 403 -> ApiResult.Forbidden
                response.code() == 404 -> ApiResult.NotFound
                else -> ApiResult.Failure(response.errorDetail())
            }
        } catch (_: IOException) {
            // No connection, DNS failure, timeout: from the app's point of view
            // these are all "the home server isn't there".
            onUnreachable?.invoke()
            ApiResult.Unreachable
        }
    }

    private suspend fun <T> call(block: suspend (TrombadarioApi) -> Response<T>): ApiResult<T> =
        request(block) { response ->
            response.body()?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure("Resposta vazia do servidor")
        }

    /** For 204 No Content endpoints, where an empty body is the success case. */
    private suspend fun callNoContent(
        block: suspend (TrombadarioApi) -> Response<Unit>,
    ): ApiResult<Unit> = request(block) { ApiResult.Success(Unit) }

    private fun <T> Response<T>.errorDetail(): String? = runCatching {
        errorBody()?.string()?.let { json.decodeFromString<com.trombadario.data.remote.ApiErrorDto>(it).detail }
    }.getOrNull()

    suspend fun login(username: String, password: String): ApiResult<TokenDto> =
        call { it.login(username, password) }.onSuccess { sessionStore.save(it.accessToken) }

    suspend fun me(): ApiResult<UserDto> = call { it.me() }

    suspend fun listEvents(childId: Int? = null): ApiResult<List<EventDto>> =
        call { it.listEvents(childId) }

    suspend fun getEvent(id: Int): ApiResult<EventDto> = call { it.getEvent(id) }

    suspend fun createEvent(event: EventCreateDto): ApiResult<EventDto> =
        call { it.createEvent(event) }

    suspend fun updateEvent(id: Int, event: EventUpdateDto): ApiResult<EventDto> =
        call { it.updateEvent(id, event) }

    suspend fun deleteEvent(id: Int): ApiResult<Unit> = callNoContent { it.deleteEvent(id) }

    suspend fun listUsers(): ApiResult<List<UserDto>> = call { it.listUsers() }

    suspend fun createUser(user: UserCreateDto): ApiResult<UserDto> = call { it.createUser(user) }

    suspend fun updateUser(id: Int, user: UserUpdateDto): ApiResult<UserDto> =
        call { it.updateUser(id, user) }

    suspend fun deleteUser(id: Int): ApiResult<Unit> = callNoContent { it.deleteUser(id) }

    fun logout() = sessionStore.clear()
}
