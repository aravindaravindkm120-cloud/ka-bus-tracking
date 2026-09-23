package com.kabus.admin.ui

import com.google.gson.JsonParser
import retrofit2.HttpException

/**
 * Best-effort human-readable message for a failed API call.
 *
 * The backend returns `{"message":"..."}` for 4xx/5xx responses, so surface
 * that instead of Retrofit's generic "HTTP 409 Conflict". Falls back to the
 * throwable's own message when the body is missing or unparseable.
 */
internal fun apiMessage(t: Throwable): String? {
    if (t is HttpException) {
        val body = runCatching { t.response()?.errorBody()?.string() }.getOrNull()
        if (!body.isNullOrBlank()) {
            val message = runCatching {
                JsonParser.parseString(body).asJsonObject.get("message")?.asString
            }.getOrNull()
            if (!message.isNullOrBlank()) return message
        }
        return t.message()
    }
    return t.message
}
