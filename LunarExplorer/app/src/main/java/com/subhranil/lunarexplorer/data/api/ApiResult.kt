package com.subhranil.lunarexplorer.data.api

/** Error codes returned by the server (see API.md). Unknown codes map to [UNKNOWN]. */
enum class ErrorCode {
    BAD_REQUEST,
    INVALID_PATH,
    NOT_A_DIRECTORY,
    IS_A_DIRECTORY,
    INVALID_OPERATION,
    UNAUTHENTICATED,
    INVALID_TOKEN,
    TOKEN_EXPIRED,
    LOGIN_FAILED,
    PERMISSION_DENIED,
    PATH_OUTSIDE_ROOTS,
    SYSTEM_ACCESS_DENIED,
    NOT_FOUND,
    ALREADY_EXISTS,
    DIRECTORY_NOT_EMPTY,
    TOO_MANY_ATTEMPTS,
    TRASH_FAILED,
    INTERNAL,
    UNKNOWN;

    companion object {
        fun parse(code: String): ErrorCode = entries.firstOrNull { it.name == code } ?: UNKNOWN
    }
}

sealed interface LunarError {
    /** The server answered with an error. [message] is the server's human-readable text. */
    data class Api(
        val code: ErrorCode,
        val message: String,
        val status: Int,
        val retryAfterSeconds: Long? = null,
    ) : LunarError

    /** The server could not be reached (no route, refused, DNS failure). */
    data object Unreachable : LunarError

    /** The server did not answer in time. */
    data object Timeout : LunarError

    /** The response could not be understood, or another unexpected failure. */
    data class Unexpected(val detail: String) : LunarError
}

sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>
    data class Failure(val error: LunarError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

fun ApiResult<*>.errorOrNull(): LunarError? = (this as? ApiResult.Failure)?.error

fun LunarError.hasCode(code: ErrorCode): Boolean = this is LunarError.Api && this.code == code
