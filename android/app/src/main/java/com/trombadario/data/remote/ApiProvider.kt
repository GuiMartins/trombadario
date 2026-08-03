package com.trombadario.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.trombadario.data.SessionStore
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Builds the Retrofit client for whatever base URL is configured. Cached by URL
 * so switching servers (or the very first pairing) rebuilds it instead of
 * silently keeping the old host.
 */
class ApiProvider(private val sessionStore: SessionStore) {

    private val json = Json { ignoreUnknownKeys = true }

    private var cachedUrl: String? = null
    private var cachedApi: TrombadarioApi? = null

    @Synchronized
    fun api(baseUrl: String): TrombadarioApi {
        cachedApi?.takeIf { cachedUrl == baseUrl }?.let { return it }

        val client = OkHttpClient.Builder()
            // Short by design: these timeouts are what turn "not at home" into a
            // quick blocking screen instead of a 30-second hang.
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor())
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TrombadarioApi::class.java)
            .also {
                cachedUrl = baseUrl
                cachedApi = it
            }
    }

    private fun authInterceptor() = Interceptor { chain ->
        val token = sessionStore.token.value
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        chain.proceed(request)
    }
}
