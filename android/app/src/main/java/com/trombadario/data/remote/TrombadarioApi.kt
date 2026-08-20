package com.trombadario.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrombadarioApi {

    @GET("api/health")
    suspend fun health(): HealthDto

    // Form-encoded, not JSON: this is FastAPI's OAuth2PasswordRequestForm.
    @FormUrlEncoded
    @POST("api/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<TokenDto>

    @GET("api/auth/me")
    suspend fun me(): Response<UserDto>

    @GET("api/unseen")
    suspend fun unseenCounts(): Response<UnseenCountsDto>

    @GET("api/birthday")
    suspend fun birthday(): Response<BirthdayDto>

    @GET("api/trombadices")
    suspend fun listTrombadices(
        @Query("child_id") childId: Int? = null,
        @Query("kind") kind: String? = null,
        @Query("category") category: String? = null,
        @Query("de") de: String? = null,
        @Query("ate") ate: String? = null,
        @Query("q") q: String? = null,
    ): Response<List<TrombadiceDto>>

    /** Os dias que têm registro, pro calendário do filtro só deixar clicar
     *  neles. Já vem respeitando os outros filtros. */
    @GET("api/trombadices/datas")
    suspend fun trombadiceDates(
        @Query("child_id") childId: Int? = null,
        @Query("kind") kind: String? = null,
        @Query("category") category: String? = null,
        @Query("q") q: String? = null,
    ): Response<DatasComRegistroDto>

    @GET("api/trombadices/{id}")
    suspend fun getTrombadice(@Path("id") id: Int): Response<TrombadiceDto>

    @POST("api/trombadices")
    suspend fun createTrombadice(@Body event: TrombadiceCreateDto): Response<TrombadiceDto>

    @PATCH("api/trombadices/{id}")
    suspend fun updateTrombadice(@Path("id") id: Int, @Body event: TrombadiceUpdateDto): Response<TrombadiceDto>

    @DELETE("api/trombadices/{id}")
    suspend fun deleteTrombadice(@Path("id") id: Int): Response<Unit>

    @GET("api/users")
    suspend fun listUsers(): Response<List<UserDto>>

    @POST("api/users")
    suspend fun createUser(@Body user: UserCreateDto): Response<UserDto>

    @PATCH("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body user: UserUpdateDto): Response<UserDto>

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<Unit>

    @GET("api/tasks")
    suspend fun listTasks(@Query("child_id") childId: Int? = null): Response<List<TaskDto>>

    @POST("api/tasks")
    suspend fun createTask(@Body task: TaskCreateDto): Response<TaskDto>

    @PATCH("api/tasks/{id}")
    suspend fun updateTask(@Path("id") id: Int, @Body task: TaskUpdateDto): Response<TaskDto>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int): Response<Unit>

    @GET("api/pedidos")
    suspend fun listPedidos(@Query("child_id") childId: Int? = null): Response<List<PedidoDto>>

    @POST("api/pedidos")
    suspend fun createPedido(@Body pedido: PedidoCreateDto): Response<PedidoDto>

    // Rota própria, não um parâmetro em cima de /api/pedidos - mesma tabela do
    // lado do servidor, mas o corpo difere (categoria só existe aqui).
    @POST("api/conquistas-propostas")
    suspend fun createPropostaConquista(
        @Body proposta: PropostaConquistaCreateDto,
    ): Response<PedidoDto>

    @PATCH("api/pedidos/{id}/decidir")
    suspend fun decidePedido(
        @Path("id") id: Int,
        @Body decision: PedidoDecisionDto,
    ): Response<PedidoDto>

    @POST("api/tasks/{id}/completions")
    suspend fun markTaskDone(
        @Path("id") id: Int,
        @Body completion: TaskCompletionCreateDto,
    ): Response<TaskCompletionDto>

    @DELETE("api/tasks/{id}/completions/{completionId}")
    suspend fun undoTaskCompletion(
        @Path("id") id: Int,
        @Path("completionId") completionId: Int,
    ): Response<Unit>

    // Assuntos pra conversar. `CurrentUser` do lado do servidor, não
    // admin/child: é a única parte do app em que os dois papéis escrevem a
    // mesma coisa - o que muda é o escopo e quem encerra.
    @GET("api/assuntos")
    suspend fun listAssuntos(
        @Query("child_id") childId: Int? = null,
        @Query("pendentes") pendentes: Boolean? = null,
    ): Response<List<AssuntoDto>>

    @POST("api/assuntos")
    suspend fun createAssunto(@Body assunto: AssuntoCreateDto): Response<AssuntoDto>

    @PATCH("api/assuntos/{id}")
    suspend fun updateAssunto(
        @Path("id") id: Int,
        @Body assunto: AssuntoUpdateDto,
    ): Response<AssuntoDto>

    /** Só o pai - o filho riscando da lista o assunto que o pai levantou
     *  apagaria a pauta do outro. O servidor barra de qualquer forma. */
    @PATCH("api/assuntos/{id}/conversado")
    suspend fun markAssuntoTalked(
        @Path("id") id: Int,
        @Body conversa: AssuntoConversaDto,
    ): Response<AssuntoDto>

    @DELETE("api/assuntos/{id}")
    suspend fun deleteAssunto(@Path("id") id: Int): Response<Unit>

    @GET("api/reports")
    suspend fun report(
        @Query("child_id") childId: Int? = null,
        @Query("de") de: String? = null,
        @Query("ate") ate: String? = null,
    ): Response<ReportDto>

    @GET("api/punishments")
    suspend fun listPunishments(
        @Query("child_id") childId: Int? = null,
    ): Response<List<PunishmentDto>>

    /** What the child's punishment screen asks: am I grounded right now? */
    @GET("api/punishments/current")
    suspend fun currentPunishments(): Response<List<PunishmentDto>>

    @POST("api/punishments")
    suspend fun createPunishment(@Body punishment: PunishmentCreateDto): Response<PunishmentDto>

    @PATCH("api/punishments/{id}")
    suspend fun updatePunishment(
        @Path("id") id: Int,
        @Body punishment: PunishmentUpdateDto,
    ): Response<PunishmentDto>

    @DELETE("api/punishments/{id}")
    suspend fun deletePunishment(@Path("id") id: Int): Response<Unit>

    @PATCH("api/punishments/{id}/reaction")
    suspend fun reactToPunishment(
        @Path("id") id: Int,
        @Body reaction: PunishmentReactionDto,
    ): Response<PunishmentDto>

    @GET("api/splash-messages")
    suspend fun listSplashMessages(): Response<List<SplashMessageDto>>

    /** Sorteado no servidor: o aparelho não influencia a escolha e nunca recebe
     *  frase escrita pra outro filho. */
    @GET("api/splash-messages/random")
    suspend fun randomSplashMessage(): Response<SplashMessageRandomDto>

    @POST("api/splash-messages")
    suspend fun createSplashMessage(
        @Body message: SplashMessageCreateDto,
    ): Response<SplashMessageDto>

    @PATCH("api/splash-messages/{id}")
    suspend fun updateSplashMessage(
        @Path("id") id: Int,
        @Body message: SplashMessageUpdateDto,
    ): Response<SplashMessageDto>

    @DELETE("api/splash-messages/{id}")
    suspend fun deleteSplashMessage(@Path("id") id: Int): Response<Unit>
}
