package com.trombadario.data

import com.trombadario.data.remote.ApiProvider
import com.trombadario.data.remote.DatasComRegistroDto
import com.trombadario.data.remote.ReportDto
import com.trombadario.data.remote.TrombadiceCreateDto
import com.trombadario.data.remote.TrombadiceDto
import com.trombadario.data.remote.TrombadiceUpdateDto
import com.trombadario.data.remote.PunishmentCreateDto
import com.trombadario.data.remote.PedidoCreateDto
import com.trombadario.data.remote.PedidoDecisionDto
import com.trombadario.data.remote.PedidoDto
import com.trombadario.data.remote.PropostaConquistaCreateDto
import com.trombadario.data.remote.PunishmentDto
import com.trombadario.data.remote.PunishmentReactionDto
import com.trombadario.data.remote.PunishmentUpdateDto
import com.trombadario.data.remote.SplashMessageCreateDto
import com.trombadario.data.remote.SplashMessageDto
import com.trombadario.data.remote.SplashMessageRandomDto
import com.trombadario.data.remote.SplashMessageUpdateDto
import com.trombadario.data.remote.TaskCompletionCreateDto
import com.trombadario.data.remote.TaskCompletionDto
import com.trombadario.data.remote.TaskCreateDto
import com.trombadario.data.remote.TaskDto
import com.trombadario.data.remote.TaskUpdateDto
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

    suspend fun listTrombadices(
        childId: Int? = null,
        kind: String? = null,
        category: String? = null,
        de: String? = null,
        ate: String? = null,
        q: String? = null,
    ): ApiResult<List<TrombadiceDto>> =
        call { it.listTrombadices(childId, kind, category, de, ate, q) }

    suspend fun trombadiceDates(
        childId: Int? = null,
        kind: String? = null,
        category: String? = null,
        q: String? = null,
    ): ApiResult<DatasComRegistroDto> = call { it.trombadiceDates(childId, kind, category, q) }

    suspend fun report(
        childId: Int? = null,
        de: String? = null,
        ate: String? = null,
    ): ApiResult<ReportDto> = call { it.report(childId, de, ate) }

    suspend fun getTrombadice(id: Int): ApiResult<TrombadiceDto> = call { it.getTrombadice(id) }

    suspend fun createTrombadice(event: TrombadiceCreateDto): ApiResult<TrombadiceDto> =
        call { it.createTrombadice(event) }

    suspend fun updateTrombadice(id: Int, event: TrombadiceUpdateDto): ApiResult<TrombadiceDto> =
        call { it.updateTrombadice(id, event) }

    suspend fun deleteTrombadice(id: Int): ApiResult<Unit> = callNoContent { it.deleteTrombadice(id) }

    suspend fun listUsers(): ApiResult<List<UserDto>> = call { it.listUsers() }

    suspend fun createUser(user: UserCreateDto): ApiResult<UserDto> = call { it.createUser(user) }

    suspend fun updateUser(id: Int, user: UserUpdateDto): ApiResult<UserDto> =
        call { it.updateUser(id, user) }

    suspend fun deleteUser(id: Int): ApiResult<Unit> = callNoContent { it.deleteUser(id) }

    suspend fun listTasks(childId: Int? = null): ApiResult<List<TaskDto>> =
        call { it.listTasks(childId) }

    suspend fun createTask(task: TaskCreateDto): ApiResult<TaskDto> = call { it.createTask(task) }

    suspend fun updateTask(id: Int, task: TaskUpdateDto): ApiResult<TaskDto> =
        call { it.updateTask(id, task) }

    suspend fun deleteTask(id: Int): ApiResult<Unit> = callNoContent { it.deleteTask(id) }

    suspend fun listTaskCompletions(taskId: Int): ApiResult<List<TaskCompletionDto>> =
        call { it.listTaskCompletions(taskId) }

    suspend fun listPedidos(childId: Int? = null): ApiResult<List<PedidoDto>> =
        call { it.listPedidos(childId) }

    suspend fun createPedido(pedido: PedidoCreateDto): ApiResult<PedidoDto> =
        call { it.createPedido(pedido) }

    suspend fun createPropostaConquista(proposta: PropostaConquistaCreateDto): ApiResult<PedidoDto> =
        call { it.createPropostaConquista(proposta) }

    suspend fun decidePedido(id: Int, decision: PedidoDecisionDto): ApiResult<PedidoDto> =
        call { it.decidePedido(id, decision) }

    suspend fun markTaskDone(
        taskId: Int,
        completion: TaskCompletionCreateDto,
    ): ApiResult<TaskCompletionDto> = call { it.markTaskDone(taskId, completion) }

    suspend fun listPunishments(childId: Int? = null): ApiResult<List<PunishmentDto>> =
        call { it.listPunishments(childId) }

    suspend fun currentPunishments(): ApiResult<List<PunishmentDto>> =
        call { it.currentPunishments() }

    suspend fun createPunishment(punishment: PunishmentCreateDto): ApiResult<PunishmentDto> =
        call { it.createPunishment(punishment) }

    suspend fun updatePunishment(
        id: Int,
        punishment: PunishmentUpdateDto,
    ): ApiResult<PunishmentDto> = call { it.updatePunishment(id, punishment) }

    suspend fun deletePunishment(id: Int): ApiResult<Unit> =
        callNoContent { it.deletePunishment(id) }

    suspend fun reactToPunishment(
        id: Int,
        reaction: PunishmentReactionDto,
    ): ApiResult<PunishmentDto> = call { it.reactToPunishment(id, reaction) }

    suspend fun listSplashMessages(): ApiResult<List<SplashMessageDto>> =
        call { it.listSplashMessages() }

    suspend fun randomSplashMessage(): ApiResult<SplashMessageRandomDto> =
        call { it.randomSplashMessage() }

    suspend fun createSplashMessage(
        message: SplashMessageCreateDto,
    ): ApiResult<SplashMessageDto> = call { it.createSplashMessage(message) }

    suspend fun updateSplashMessage(
        id: Int,
        message: SplashMessageUpdateDto,
    ): ApiResult<SplashMessageDto> = call { it.updateSplashMessage(id, message) }

    suspend fun deleteSplashMessage(id: Int): ApiResult<Unit> =
        callNoContent { it.deleteSplashMessage(id) }

    fun logout() = sessionStore.clear()
}
