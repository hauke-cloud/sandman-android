package cloud.hauke.sandman.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.hauke.sandman.data.model.Condition
import cloud.hauke.sandman.data.model.Device
import cloud.hauke.sandman.data.remote.PowerAction
import cloud.hauke.sandman.ui.components.EmptyState
import cloud.hauke.sandman.ui.components.InfoPill
import cloud.hauke.sandman.ui.components.PhaseBadge
import cloud.hauke.sandman.ui.components.PowerActionDialog
import cloud.hauke.sandman.ui.components.absoluteTime
import cloud.hauke.sandman.ui.components.relativeTime
import cloud.hauke.sandman.ui.ReportVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
  viewModel: DeviceDetailViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  ReportVisibility(viewModel::onVisibilityChanged)

  LaunchedEffect(viewModel) {
    viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
  }

  var confirming by remember { mutableStateOf<PowerAction?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(state.deviceName) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
    ) {
      val device = state.device
      when {
        device != null -> DeviceDetail(
          device = device,
          pending = state.pending,
          onRequest = { confirming = it },
        )

        state.error != null -> EmptyState(
          icon = Icons.Default.Warning,
          title = state.error?.message.orEmpty(),
          body = state.error?.hint.orEmpty(),
          actionLabel = "Try again",
          onAction = viewModel::refresh,
        )

        else -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
      }

      if (state.loading && state.device != null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }
  }

  confirming?.let { action ->
    PowerActionDialog(
      action = action,
      deviceName = state.deviceName,
      nodeName = state.device?.node?.ifBlank { state.deviceName } ?: state.deviceName,
      onDismiss = { confirming = null },
      onConfirm = { reason ->
        confirming = null
        when (action) {
          PowerAction.Start -> viewModel.start(reason)
          PowerAction.Stop -> viewModel.stop(reason)
          PowerAction.Release -> viewModel.release(reason)
        }
      },
    )
  }
}

@Composable
private fun DeviceDetail(
  device: Device,
  pending: Boolean,
  onRequest: (PowerAction) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    StateCard(device)
    ActionCard(device = device, pending = pending, onRequest = onRequest)
    FactsCard(device)
    TimestampsCard(device)
    if (device.conditions.isNotEmpty()) {
      ConditionsCard(device.conditions)
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
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
private fun StateCard(device: Device) {
  SectionCard(title = "State") {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      PhaseBadge(device.state)
      if (device.paused) InfoPill(text = "Paused")
      if (device.nodeStatus.unschedulable == true) InfoPill(text = "Cordoned")
    }

    relativeTime(device.timestamps.phaseSince)?.let {
      Text(
        text = "In this state since $it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    device.message?.takeIf { it.isNotBlank() }?.let {
      Text(text = it, style = MaterialTheme.typography.bodyMedium)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      Fact(label = "Desired", value = device.desiredState.name, modifier = Modifier.weight(1f))
      Fact(
        label = "Node",
        value = when {
          !device.nodeStatus.found -> "not registered"
          device.nodeStatus.ready == true -> "Ready"
          device.nodeStatus.ready == false -> "NotReady"
          else -> "registered"
        },
        modifier = Modifier.weight(1f),
      )
      Fact(label = "Reason", value = device.reason.orEmpty().ifBlank { "—" }, modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun ActionCard(
  device: Device,
  pending: Boolean,
  onRequest: (PowerAction) -> Unit,
) {
  SectionCard(title = "Power") {
    if (pending) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
      ) { CircularProgressIndicator() }
      return@SectionCard
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = { onRequest(PowerAction.Start) },
        enabled = device.canStart,
        modifier = Modifier.weight(1f),
      ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Text(text = "Start", modifier = Modifier.padding(start = 8.dp))
      }
      Button(
        onClick = { onRequest(PowerAction.Stop) },
        enabled = device.canStop,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.weight(1f),
      ) {
        Icon(Icons.Default.Clear, contentDescription = null)
        Text(text = "Stop", modifier = Modifier.padding(start = 8.dp))
      }
    }

    OutlinedButton(
      onClick = { onRequest(PowerAction.Release) },
      enabled = !device.inFlight,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Release (stop managing)")
    }

    if (device.wakeAttempts > 0) {
      Text(
        text = "${device.wakeAttempts} magic packet(s) sent during the current start.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    device.shutdownPod?.takeIf { it.isNotBlank() }?.let {
      Text(
        text = "Shutdown pod: $it",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}

@Composable
private fun FactsCard(device: Device) {
  SectionCard(title = "Machine") {
    device.description?.takeIf { it.isNotBlank() }?.let {
      Text(text = it, style = MaterialTheme.typography.bodyMedium)
    }
    Fact(label = "Node", value = device.node.ifBlank { "—" }, monospace = true)
    Fact(label = "MAC address", value = device.macAddress.ifBlank { "—" }, monospace = true)
    Fact(label = "Broadcast", value = device.broadcastAddress.ifBlank { "—" }, monospace = true)
    Fact(label = "Address", value = device.address.orEmpty().ifBlank { "—" }, monospace = true)
  }
}

@Composable
private fun TimestampsCard(device: Device) {
  val rows = listOfNotNull(
    device.timestamps.lastStarted?.let { "Last started" to it },
    device.timestamps.lastStopped?.let { "Last stopped" to it },
    device.timestamps.lastWake?.let { "Last wake sent" to it },
    device.timestamps.lastShutdown?.let { "Last shutdown" to it },
    device.timestamps.phaseSince?.let { "Phase since" to it },
  )
  if (rows.isEmpty()) return

  SectionCard(title = "History") {
    rows.forEach { (label, value) ->
      Fact(
        label = label,
        value = absoluteTime(value) ?: value,
        caption = relativeTime(value),
      )
    }
  }
}

@Composable
private fun ConditionsCard(conditions: List<Condition>) {
  SectionCard(title = "Conditions") {
    conditions.forEachIndexed { index, condition ->
      if (index > 0) HorizontalDivider()
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "${condition.type}: ${condition.status}",
          style = MaterialTheme.typography.bodyMedium,
        )
        condition.reason?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        condition.message?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun Fact(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  caption: String? = null,
  monospace: Boolean = false,
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontFamily = if (monospace) FontFamily.Monospace else null,
      textAlign = TextAlign.Start,
    )
    if (caption != null) {
      Text(
        text = caption,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
