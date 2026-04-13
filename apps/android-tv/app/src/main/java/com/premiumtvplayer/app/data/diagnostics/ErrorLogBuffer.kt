package com.premiumtvplayer.app.data.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory circular buffer for recent client-side errors. Populated
 * by a shared Hilt-injected singleton so every layer (ViewModels,
 * Repos, OkHttp interceptors) can record + inspect.
 *
 * Capacity is deliberate: 50 rows is enough for a support screenshot
 * and small enough that it never shows up in OOM reports.
 */
@Singleton
class ErrorLogBuffer @Inject constructor() {

    data class Entry(
        val at: Instant,
        val source: String,
        val message: String,
        val code: String? = null,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(source: String, message: String, code: String? = null) {
        val entry = Entry(at = Instant.now(), source = source, message = message, code = code)
        _entries.value = (listOf(entry) + _entries.value).take(CAPACITY)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val CAPACITY = 50
    }
}
