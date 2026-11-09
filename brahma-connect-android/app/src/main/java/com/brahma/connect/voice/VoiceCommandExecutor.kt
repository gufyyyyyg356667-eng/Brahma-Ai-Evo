package com.brahma.connect.voice

import com.brahma.connect.commands.DeviceCommandHandler
import com.brahma.connect.core.CommandResult

sealed class VoiceOutcome {
    /** The utterance did not match any supported command. Say so to the user. */
    object NotUnderstood : VoiceOutcome()

    /** A sensitive command was recognized. It runs only after the user confirms. */
    data class NeedsConfirmation(val intent: VoiceIntent) : VoiceOutcome()

    /** The command ran. `result` is what DeviceCommandHandler reported. */
    data class Executed(val intent: VoiceIntent, val result: CommandResult) : VoiceOutcome()
}

/**
 * Connects the parser to the device command handler.
 * Sensitive intents (SMS, call) are never executed directly from speech.
 */
class VoiceCommandExecutor(private val handler: DeviceCommandHandler) {

    fun handle(utterance: String): VoiceOutcome {
        val intent = VoiceCommandParser.parse(utterance) ?: return VoiceOutcome.NotUnderstood
        return if (intent.sensitive) {
            VoiceOutcome.NeedsConfirmation(intent)
        } else {
            run(intent)
        }
    }

    /** Called only after the user has explicitly confirmed a sensitive intent. */
    fun confirm(intent: VoiceIntent): VoiceOutcome.Executed = run(intent)

    private fun run(intent: VoiceIntent): VoiceOutcome.Executed {
        val result = handler.handle(intent.action, intent.parameters)
        return VoiceOutcome.Executed(intent, result)
    }
}
