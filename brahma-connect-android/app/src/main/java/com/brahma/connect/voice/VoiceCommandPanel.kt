package com.brahma.connect.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Microphone control and confirmation prompt. Stateless: all state comes from [VoiceController].
 * [onRequestMicPermission] must request RECORD_AUDIO and call startListening once granted.
 */
@Composable
fun VoiceCommandPanel(
    controller: VoiceController,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
) {
    val state by controller.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.message?.let { Text(it) }

        if (state.pending != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller.confirmPending() }) { Text("Confirm") }
                OutlinedButton(onClick = { controller.cancelPending() }) { Text("Cancel") }
            }
        } else {
            Button(
                enabled = !state.listening,
                onClick = {
                    if (hasMicPermission) controller.startListening() else onRequestMicPermission()
                },
            ) {
                Text(if (state.listening) "Listening…" else "🎤 Speak")
            }
        }
    }
}
