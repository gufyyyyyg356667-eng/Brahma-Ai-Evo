package com.brahma.connect.voice

import com.brahma.connect.commands.DeviceCommandHandler
import com.brahma.connect.core.CommandResult

sealed class VoiceOutcome {
    /** The utterance did not match any supported command. Say so to the user. */
    object NotUnderstood : VoiceOutcome()

    /** The command could not be prepared, for example an unknown or ambiguous contact. */
    data class Failed(val message: String) : VoiceOutcome()

    /** A sensitive command was recognized and resolved. It runs only after the user confirms. */
    data class NeedsConfirmation(val intent: VoiceIntent) : VoiceOutcome()

    /** The command ran. `result` is what DeviceCommandHandler reported. */
    data class Executed(val intent: VoiceIntent, val result: CommandResult) : VoiceOutcome()
}

/**
 * Connects the parser to the contact lookup and the device command handler.
 * Sensitive intents (SMS, call) are never executed directly from speech.
 */
class VoiceCommandExecutor(
    private val handler: DeviceCommandHandler,
    private val contacts: ContactLookup,
) {

    fun handle(utterance: String): VoiceOutcome {
        val parsed = VoiceCommandParser.parse(utterance) ?: return VoiceOutcome.NotUnderstood
        val intent = when (val resolved = resolveContact(parsed)) {
            is Resolution.Ok -> resolved.intent
            is Resolution.Error -> return VoiceOutcome.Failed(resolved.message)
        }
        return if (intent.sensitive) {
            VoiceOutcome.NeedsConfirmation(intent)
        } else {
            run(intent)
        }
    }

    /** Called only after the user has explicitly confirmed a sensitive intent. */
    fun confirm(intent: VoiceIntent): VoiceOutcome.Executed = run(intent)

    private sealed class Resolution {
        data class Ok(val intent: VoiceIntent) : Resolution()
        data class Error(val message: String) : Resolution()
    }

    private fun resolveContact(intent: VoiceIntent): Resolution {
        val name = intent.parameters["contact"]?.toString() ?: return Resolution.Ok(intent)
        return when (val match = contacts.find(name)) {
            is ContactMatch.Found -> Resolution.Ok(
                intent.copy(
                    parameters = intent.parameters - "contact" +
                        ("number" to match.number) + ("contact_name" to match.name),
                )
            )
            is ContactMatch.Ambiguous -> Resolution.Error(
                "More than one contact matches \"$name\": ${match.names.joinToString()}. Say the full name or the number."
            )
            ContactMatch.NotFound -> Resolution.Error("No contact named \"$name\".")
            ContactMatch.PermissionDenied -> Resolution.Error(
                "Contacts permission is needed to call or text by name. Allow it in the app settings, or say the number."
            )
        }
    }

    private fun run(intent: VoiceIntent): VoiceOutcome.Executed {
        val result = handler.handle(intent.action, intent.parameters)
        return VoiceOutcome.Executed(intent, result)
    }
}
