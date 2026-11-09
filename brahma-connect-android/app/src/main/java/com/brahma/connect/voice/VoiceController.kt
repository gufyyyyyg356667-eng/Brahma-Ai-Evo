package com.brahma.connect.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceState(
    val listening: Boolean = false,
    val lastHeard: String? = null,
    val message: String? = null,
    /** A sensitive command waiting for the user to confirm or cancel. */
    val pending: VoiceIntent? = null,
)

/**
 * Coordinates one voice interaction: listen, parse, execute or ask for confirmation.
 * The UI observes [state] and calls [startListening], [confirmPending] or [cancelPending].
 */
class VoiceController(
    private val input: VoiceInput,
    private val executor: VoiceCommandExecutor,
) {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    fun startListening() {
        _state.value = _state.value.copy(listening = true, message = "Listening…", pending = null)
        input.listen(
            onText = { text -> onTranscript(text) },
            onFailure = { reason ->
                _state.value = _state.value.copy(listening = false, message = reason)
            },
        )
    }

    fun stopListening() {
        input.stop()
        _state.value = _state.value.copy(listening = false)
    }

    /** Public so that typed text can use the same path as speech. */
    fun onTranscript(text: String) {
        val outcome = executor.handle(text)
        _state.value = when (outcome) {
            is VoiceOutcome.NotUnderstood -> _state.value.copy(
                listening = false,
                lastHeard = text,
                message = "I did not understand: \"$text\"",
                pending = null,
            )
            is VoiceOutcome.NeedsConfirmation -> _state.value.copy(
                listening = false,
                lastHeard = text,
                message = describeConfirmation(outcome.intent),
                pending = outcome.intent,
            )
            is VoiceOutcome.Executed -> _state.value.copy(
                listening = false,
                lastHeard = text,
                message = describeResult(outcome),
                pending = null,
            )
        }
    }

    fun confirmPending() {
        val intent = _state.value.pending ?: return
        val outcome = executor.confirm(intent)
        _state.value = _state.value.copy(message = describeResult(outcome), pending = null)
    }

    fun cancelPending() {
        _state.value = _state.value.copy(message = "Cancelled.", pending = null)
    }

    fun release() {
        input.release()
    }

    private fun describeConfirmation(intent: VoiceIntent): String = when (intent.action) {
        "send_sms" -> "Send SMS to ${intent.parameters["number"]}? Tap Confirm to send."
        "make_call" -> "Call ${intent.parameters["number"]}? Tap Confirm to call."
        else -> "Confirm ${intent.action}?"
    }

    private fun describeResult(outcome: VoiceOutcome): String = when (outcome) {
        is VoiceOutcome.Executed -> if (outcome.result.success) {
            "Done: ${outcome.intent.action}."
        } else {
            "Failed: ${outcome.result.error ?: outcome.result.errorCode ?: "unknown error"}"
        }
        is VoiceOutcome.NeedsConfirmation -> describeConfirmation(outcome.intent)
        is VoiceOutcome.NotUnderstood -> "I did not understand that."
    }
}
