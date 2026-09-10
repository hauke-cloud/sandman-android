package cloud.hauke.sandman.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.hauke.sandman.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var tokenVisible by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Section(title = "Server") {
        OutlinedTextField(
          value = state.settings.baseUrl,
          onValueChange = viewModel::onBaseUrlChanged,
          label = { Text("Address") },
          placeholder = { Text("http://sandman.lab:8080") },
          supportingText = { Text("The root of the sandman API. https is used as given; http is assumed otherwise.") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = state.settings.token,
          onValueChange = viewModel::onTokenChanged,
          label = { Text("Bearer token") },
          supportingText = { Text("Leave empty if this sandman runs without a token.") },
          singleLine = true,
          visualTransformation =
            if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          trailingIcon = {
            TextButton(onClick = { tokenVisible = !tokenVisible }) {
              Text(if (tokenVisible) "Hide" else "Show")
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Button(
            onClick = viewModel::testConnection,
            enabled = state.settings.isConfigured && state.probe !is ProbeResult.Running,
          ) {
            Text("Test connection")
          }
          ProbeStatus(state.probe)
        }
      }

      Section(title = "Requests") {
        OutlinedTextField(
          value = state.settings.requestedBy,
          onValueChange = viewModel::onRequestedByChanged,
          label = { Text("Your name") },
          supportingText = {
            Text("sandman annotates every start and stop with this. Left empty, the phone's model is used.")
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        SwitchRow(
          title = "Confirm before stopping",
          subtitle = "Stopping a node drains it and moves its workloads.",
          checked = state.settings.confirmStop,
          onCheckedChange = viewModel::onConfirmStopChanged,
        )
      }

      Section(title = "Refresh") {
        SwitchRow(
          title = "Refresh automatically",
          subtitle = "Only while a screen is in front of you. A machine that is starting or stopping is polled faster.",
          checked = state.settings.autoRefresh,
          onCheckedChange = viewModel::onAutoRefreshChanged,
        )

        if (state.settings.autoRefresh) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Settings.REFRESH_CHOICES.forEach { seconds ->
              FilterChip(
                selected = state.settings.refreshSeconds == seconds,
                onClick = { viewModel.onRefreshSecondsChanged(seconds) },
                label = { Text("${seconds}s") },
              )
            }
          }
        }
      }

      Text(
        text = "sandman-android · cloud.hauke.sandman",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}

@Composable
private fun ProbeStatus(probe: ProbeResult) {
  when (probe) {
    ProbeResult.Idle -> Unit

    ProbeResult.Running -> CircularProgressIndicator(
      modifier = Modifier.size(20.dp),
      strokeWidth = 2.dp,
    )

    is ProbeResult.Success -> Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = when (probe.deviceCount) {
          0 -> "Connected, no devices"
          1 -> "Connected, 1 device"
          else -> "Connected, ${probe.deviceCount} devices"
        },
        style = MaterialTheme.typography.bodySmall,
      )
    }

    is ProbeResult.Failure -> Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = probe.error.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
private fun Section(
  title: String,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      content()
    }
  }
}

@Composable
private fun SwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
