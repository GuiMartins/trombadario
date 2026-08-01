package com.trombadario.data

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    /** Token missing, expired or rejected - the session is over. */
    data object Unauthorized : ApiResult<Nothing>

    /** The server refused the action for this role. Reaching this from the UI
     *  means a screen offered something it shouldn't have. */
    data object Forbidden : ApiResult<Nothing>

    data object NotFound : ApiResult<Nothing>

    /** Could not talk to the server at all. Treated as "not at home". */
    data object Unreachable : ApiResult<Nothing>

    data class Failure(val message: String?) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}
