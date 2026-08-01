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

    @GET("api/events")
    suspend fun listEvents(@Query("child_id") childId: Int? = null): Response<List<EventDto>>

    @GET("api/events/{id}")
    suspend fun getEvent(@Path("id") id: Int): Response<EventDto>

    @POST("api/events")
    suspend fun createEvent(@Body event: EventCreateDto): Response<EventDto>

    @PATCH("api/events/{id}")
    suspend fun updateEvent(@Path("id") id: Int, @Body event: EventUpdateDto): Response<EventDto>

    @DELETE("api/events/{id}")
    suspend fun deleteEvent(@Path("id") id: Int): Response<Unit>

    @GET("api/users")
    suspend fun listUsers(): Response<List<UserDto>>

    @POST("api/users")
    suspend fun createUser(@Body user: UserCreateDto): Response<UserDto>

    @PATCH("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body user: UserUpdateDto): Response<UserDto>

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<Unit>
}
