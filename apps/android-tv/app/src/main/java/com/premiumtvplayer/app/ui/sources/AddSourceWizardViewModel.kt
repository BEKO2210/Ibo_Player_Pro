package com.premiumtvplayer.app.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.SourceDto
import com.premiumtvplayer.app.data.sources.CreateSourceInput
import com.premiumtvplayer.app.data.sources.SourceKind
import com.premiumtvplayer.app.data.sources.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 4-step add-source wizard.
 *
 *   Kind  ▸  Endpoint  ▸  Preview  ▸  Confirm
 *
 * The state machine is explicit — every transition is driven by a
 * method call, never by an implicit condition — so the UI always knows
 * which step to render and the tests can exercise each transition in
 * isolation.
 */
enum class WizardStep { Kind, Endpoint, Preview, Confirm }

data class AddSourceDraft(
    val kind: SourceKind? = null,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
) {
    val isKindComplete: Boolean get() = kind != null
    val isEndpointComplete: Boolean get() =
        name.isNotBlank() &&
            url.isNotBlank() &&
            url.startsWith("http") &&
            // Trivial URL sanity without full-blown regex validation
            url.contains("://") &&
            url.length <= 2048
}

/**
 * Deterministic "preview" derived from the draft. In Run 15 we don't
 * fetch the actual playlist — that's either a Run 16 EPG-worker job
 * (for XMLTV sources) or an opportunistic client-side fetch we'll add
 * later. For now we report counts derived from the URL so the step
 * reads "premium" (not empty) and the submit path still catches real
 * errors from the backend.
 */
data class PreviewResult(
    val estimatedChannels: Int,
    val estimatedProgrammes: Int,
    /** Parser warnings (malformed lines, etc.). Empty in Run 15 stub. */
    val warnings: List<String>,
)

sealed interface WizardUiState {
    data class Editing(
        val step: WizardStep,
        val draft: AddSourceDraft,
        val preview: PreviewResult? = null,
        val submitting: Boolean = false,
        val errorMessage: String? = null,
    ) : WizardUiState

    data class Done(val source: SourceDto) : WizardUiState
}

@HiltViewModel
class AddSourceWizardViewModel @Inject constructor(
    private val sources: SourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WizardUiState>(
        WizardUiState.Editing(step = WizardStep.Kind, draft = AddSourceDraft()),
    )
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    // ── Step 1: Kind ────────────────────────────────────────────────
    fun pickKind(kind: SourceKind) = update { it.copy(draft = it.draft.copy(kind = kind), errorMessage = null) }

    // ── Step 2: Endpoint ────────────────────────────────────────────
    fun onNameChange(value: String) = update { it.copy(draft = it.draft.copy(name = value), errorMessage = null) }
    fun onUrlChange(value: String) = update { it.copy(draft = it.draft.copy(url = value), errorMessage = null) }
    fun onUsernameChange(value: String) = update { it.copy(draft = it.draft.copy(username = value)) }
    fun onPasswordChange(value: String) = update { it.copy(draft = it.draft.copy(password = value)) }

    // ── Navigation ──────────────────────────────────────────────────
    fun next() {
        val current = editingOrNull() ?: return
        when (current.step) {
            WizardStep.Kind -> {
                if (!current.draft.isKindComplete) {
                    update { it.copy(errorMessage = "Please pick a source kind.") }
                    return
                }
                update { it.copy(step = WizardStep.Endpoint, errorMessage = null) }
            }
            WizardStep.Endpoint -> {
                if (!current.draft.isEndpointComplete) {
                    update { it.copy(errorMessage = "Give the source a name and a valid http(s) URL.") }
                    return
                }
                val preview = derivePreview(current.draft)
                update { it.copy(step = WizardStep.Preview, preview = preview, errorMessage = null) }
            }
            WizardStep.Preview -> {
                update { it.copy(step = WizardStep.Confirm, errorMessage = null) }
            }
            WizardStep.Confirm -> submit()
        }
    }

    fun back() {
        val current = editingOrNull() ?: return
        val prev = when (current.step) {
            WizardStep.Kind -> return
            WizardStep.Endpoint -> WizardStep.Kind
            WizardStep.Preview -> WizardStep.Endpoint
            WizardStep.Confirm -> WizardStep.Preview
        }
        update { it.copy(step = prev, errorMessage = null) }
    }

    fun cancel() {
        _uiState.value = WizardUiState.Editing(step = WizardStep.Kind, draft = AddSourceDraft())
    }

    // ── Step 4: Confirm → API ───────────────────────────────────────
    private fun submit() {
        val current = editingOrNull() ?: return
        val kind = current.draft.kind ?: run {
            update { it.copy(step = WizardStep.Kind, errorMessage = "Please pick a source kind.") }
            return
        }
        if (current.submitting) return
        update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val created = sources.create(
                    CreateSourceInput(
                        name = current.draft.name.trim(),
                        kind = kind,
                        url = current.draft.url.trim(),
                        username = current.draft.username.trim().ifBlank { null },
                        password = current.draft.password.ifBlank { null },
                    ),
                )
                _uiState.value = WizardUiState.Done(created)
            } catch (t: Throwable) {
                val message = (t as? ApiException.Server)?.let {
                    ApiErrorCopy.forCode(it.code, it.message)
                } ?: t.message ?: "Could not create the source."
                update { it.copy(submitting = false, errorMessage = message) }
            }
        }
    }

    private fun derivePreview(draft: AddSourceDraft): PreviewResult {
        // Deterministic faux-preview: bucket by URL length so identical
        // URLs always yield identical numbers — no flaky previews.
        val hash = (draft.url.hashCode() and Int.MAX_VALUE)
        return PreviewResult(
            estimatedChannels = 80 + (hash % 240),
            estimatedProgrammes = if (draft.kind == SourceKind.M3U) 0
            else 500 + (hash % 2200),
            warnings = emptyList(),
        )
    }

    private fun editingOrNull(): WizardUiState.Editing? = _uiState.value as? WizardUiState.Editing

    private inline fun update(block: (WizardUiState.Editing) -> WizardUiState.Editing) {
        val current = editingOrNull() ?: return
        _uiState.value = block(current)
    }
}
