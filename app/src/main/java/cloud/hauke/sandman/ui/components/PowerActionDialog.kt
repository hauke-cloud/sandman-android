package cloud.hauke.sandman.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cloud.hauke.sandman.data.remote.PowerAction

/**
 * The confirmation in front of a power change.
 *
 * It doubles as the place to type a reason, which sandman keeps on the device
 * as an annotation. Asking for it here is the only moment anyone will actually
 * write one down.
 */
@Composable
fun PowerActionDialog(
  action: PowerAction,
  deviceName: String,
  nodeName: String,
  onDismiss: () -> Unit,
  onConfirm: (reason: String?) -> Unit,
) {
  var reason by remember { mutableStateOf("") }

  val title = when (action) {
    PowerAction.Start -> "Start $deviceName?"
    PowerAction.Stop -> "Stop $deviceName?"
    PowerAction.Release -> "Release $deviceName?"
  }

  val body = when (action) {
    PowerAction.Start -> "sandman will send magic packets until $nodeName registers and reports Ready."
    PowerAction.Stop ->
      "$nodeName will be cordoned and, where draining is enabled for it, drained before it powers off."

    PowerAction.Release ->
      "sandman will stop acting on $deviceName. Its state is still reported, but nothing will be started or stopped."
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("Reason (optional)") },
          supportingText = { Text("Recorded on the device alongside your name.") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(reason.ifBlank { null }) }) {
        Text(
          when (action) {
            PowerAction.Start -> "Start"
            PowerAction.Stop -> "Stop"
            PowerAction.Release -> "Release"
          },
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
