package com.premiumtvplayer.app.data.api

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * A normalized error type for everything that can go wrong calling the
 * V1 API. Surfaces enough information for ViewModels to render
 * user-facing messages without leaking HTTP plumbing into the UI layer.
 */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Server returned a stable ErrorEnvelope. `code` matches one of
     *  `ErrorCode` from `services/api/src/common/errors.ts`. */
    data class Server(
        val httpStatus: Int,
        val code: String,
        override val message: String,
        val requestId: String? = null,
    ) : ApiException(message)

    /** Network unreachable, DNS failure, timeout — pre-HTTP. */
    data class Network(override val message: String, val rootCause: Throwable) :
        ApiException(message, rootCause)

    /** Anything we couldn't classify (parse error, unexpected shape). */
    data class Unknown(override val message: String, val rootCause: Throwable) :
        ApiException(message, rootCause)
}

object ApiErrorMapper {
    private val parser = Json { ignoreUnknownKeys = true }

    /**
     * Translate an arbitrary throwable thrown by Retrofit/OkHttp into our
     * normalized [ApiException]. Use from a ViewModel's catch block.
     */
    fun map(t: Throwable): ApiException = when (t) {
        is ApiException -> t
        is HttpException -> mapHttp(t)
        is IOException -> ApiException.Network(
            message = t.message ?: "Network unreachable",
            rootCause = t,
        )
        else -> ApiException.Unknown(
            message = t.message ?: "Unexpected error",
            rootCause = t,
        )
    }

    private fun mapHttp(httpException: HttpException): ApiException {
        val rawBody = httpException.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { parser.decodeFromString<ApiErrorEnvelope>(rawBody) }.getOrNull()
        return if (parsed != null) {
            ApiException.Server(
                httpStatus = httpException.code(),
                code = parsed.error.code,
                message = parsed.error.message,
                requestId = parsed.error.requestId,
            )
        } else {
            ApiException.Server(
                httpStatus = httpException.code(),
                code = "VALIDATION_ERROR",
                message = httpException.message ?: "HTTP ${httpException.code()}",
            )
        }
    }
}

/**
 * User-facing copy for known error codes.
 *
 * Run 19 migration: `forCode(code, fallback)` kept for ViewModels that
 * still emit raw strings. New callers should prefer [resourceForCode]
 * and resolve via `stringResource(id)` in Composables — that picks up
 * the active locale automatically (see `values-de/strings.xml`).
 */
object ApiErrorCopy {
    fun forCode(code: String, fallback: String): String = when (code) {
        "UNAUTHORIZED" -> "Your session expired. Please sign in again."
        "ENTITLEMENT_REQUIRED" -> "Your entitlement doesn't allow this action."
        "SLOT_FULL" -> "You've reached the device or profile limit for your plan."
        "PIN_INVALID" -> "Wrong PIN. Try again."
        "VALIDATION_ERROR" -> "Something's off with the request. Please try again."
        else -> fallback
    }

    /**
     * Returns the `R.string.*` resource id for a known error code. The
     * resource lookup happens at the Composable layer via
     * `stringResource(id)` so the active locale wins automatically.
     *
     * Returns null for unknown codes — caller falls back to the raw
     * server message.
     */
    fun resourceForCode(code: String): Int? = when (code) {
        "UNAUTHORIZED" -> com.premiumtvplayer.app.R.string.error_unauthorized
        "ENTITLEMENT_REQUIRED" -> com.premiumtvplayer.app.R.string.error_entitlement_required
        "SLOT_FULL" -> com.premiumtvplayer.app.R.string.error_slot_full
        "PIN_INVALID" -> com.premiumtvplayer.app.R.string.error_pin_invalid
        "VALIDATION_ERROR" -> com.premiumtvplayer.app.R.string.error_validation
        else -> null
    }
}

/**
 * A localized error message payload returned by [ApiErrorMapper.toMessage].
 * Prefer this over raw strings in ViewModels: the Composable layer can
 * then resolve to the active locale via `stringResource(resId)`.
 */
sealed interface UserErrorMessage {
    /** Raw string (server-provided fallback or network layer). */
    data class Raw(val message: String) : UserErrorMessage

    /** A localized message backed by a string resource. */
    data class Resource(val resId: Int) : UserErrorMessage

    /** A localized message + a positional string arg (for "%1$s"-style keys). */
    data class ResourceWithArg(val resId: Int, val arg: String) : UserErrorMessage
}

/** Translate any throwable into a UI-ready message token. */
fun ApiException.toUserMessage(): UserErrorMessage {
    if (this is ApiException.Server) {
        val res = ApiErrorCopy.resourceForCode(code)
        if (res != null) return UserErrorMessage.Resource(res)
        return UserErrorMessage.Raw(message)
    }
    if (this is ApiException.Network) {
        return UserErrorMessage.Resource(com.premiumtvplayer.app.R.string.error_network)
    }
    return UserErrorMessage.Raw(message ?: "")
}
