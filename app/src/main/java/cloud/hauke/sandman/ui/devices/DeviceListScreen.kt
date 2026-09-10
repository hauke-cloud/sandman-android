package cloud.hauke.sandman.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.hauke.sandman.data.model.Device
import cloud.hauke.sandman.data.remote.PowerAction
import cloud.hauke.sandman.ui.components.EmptyState
import cloud.hauke.sandman.ui.components.InfoPill
import cloud.hauke.sandman.ui.components.PhaseBadge
import cloud.hauke.sandman.ui.components.PowerActionDialog
import cloud.hauke.sandman.ui.components.relativeTime
import cloud.hauke.sandman.ui.ReportVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
  viewModel: DeviceListViewModel,
  onOpenDevice: (String) -> Unit,
  onOpenSettings: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  ReportVisibility(viewModel::onVisibilityChanged)

  LaunchedEffect(viewModel) {
    viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
  }

  var confirming by remember { mutableStateOf<Pair<Device, PowerAction>?>(null) }

  fun request(device: Device, action: PowerAction) {
    // Starting a machine is cheap and reversible, so it happens on one tap.
    // Stopping one moves workloads, and releasing one quietly changes who is
    // in charge of it; both are worth a sentence in front of them.
    val needsConfirmation = when (action) {
      PowerAction.Start -> false
      PowerAction.Stop -> state.settings.confirmStop
      PowerAction.Release -> true
    }
    if (needsConfirmation) {
      confirming = device to action
    } else {
      when (action) {
        PowerAction.Start -> viewModel.start(device, null)
        PowerAction.Stop -> viewModel.stop(device, null)
        PowerAction.Release -> viewModel.release(device, null)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Sandman") },
        actions = {
          IconButton(onClick = viewModel::refresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
          }
          IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
      when {
        !state.isConfigured -> EmptyState(
          icon = Icons.Default.Settings,
          title = "No sandman configured",
          body = "Point the app at a sandman instance to see the machines it manages.",
          actionLabel = "Open settings",
          onAction = onOpenSettings,
        )

        state.error != null && state.devices.isEmpty() -> EmptyState(
          icon = Icons.Default.Warning,
          title = state.error?.message.orEmpty(),
          body = state.error?.hint.orEmpty(),
          actionLabel = if (state.error?.requiresToken == true) "Open settings" else "Try again",
          onAction = if (state.error?.requiresToken == true) onOpenSettings else viewModel::refresh,
        )

        state.devices.isEmpty() && state.loadedAtLeastOnce -> EmptyState(
          icon = Icons.Default.Info,
          title = "No devices",
          body = "This sandman is not managing any SandmanDevice yet.",
          actionLabel = "Refresh",
          onAction = viewModel::refresh,
        )

        state.devices.isEmpty() -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        else -> DeviceList(
          state = state,
          onOpenDevice = onOpenDevice,
          onRequest = ::request,
        )
      }

      // A thin line rather than a spinner: a poll every fifteen seconds should
      // not make the screen flicker.
      if (state.loading && state.devices.isNotEmpty()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }
  }

  confirming?.let { (device, action) ->
    PowerActionDialog(
      action = action,
      deviceName = device.name,
      nodeName = device.node.ifBlank { device.name },
      onDismiss = { confirming = null },
      onConfirm = { reason ->
        confirming = null
        when (action) {
          PowerAction.Start -> viewModel.start(device, reason)
          PowerAction.Stop -> viewModel.stop(device, reason)
          PowerAction.Release -> viewModel.release(device, reason)
        }
      },
    )
  }
}

@Composable
private fun DeviceList(
  state: DeviceListUiState,
  onOpenDevice: (String) -> Unit,
  onRequest: (Device, PowerAction) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.summary.isNotEmpty()) {
      item(key = "summary") { FleetSummary(state.summary) }
    }
    items(state.devices, key = { it.name }) { device ->
      DeviceCard(
        device = device,
        pending = device.name in state.pending,
        onOpen = { onOpenDevice(device.name) },
        onRequest = { action -> onRequest(device, action) },
      )
    }
    if (state.error != null) {
      item(key = "error") {
        // The list is still shown: a poll that failed does not make the last
        // known state useless, it just makes it old.
        Text(
          text = "Last refresh failed: ${state.error.message}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun FleetSummary(summary: Map<String, Int>) {
  val ordered = listOf("Started", "Starting", "Pending", "Stopping", "Stopped", "Failed", "Unknown")
    .mapNotNull { phase -> summary[phase]?.takeIf { it > 0 }?.let { phase to it } }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ordered.forEach { (phase, count) ->
      Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Column(
          modifier = Modifier.padding(vertical = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(text = "$count", style = MaterialTheme.typography.titleLarge)
          Text(
            text = phase,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun DeviceCard(
  device: Device,
  pending: Boolean,
  onOpen: () -> Unit,
  onRequest: (PowerAction) -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = device.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = device.node.ifBlank { "no node" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        PhaseBadge(device.state)
      }

      val subtitle = listOfNotNull(
        device.message?.takeIf { it.isNotBlank() },
        relativeTime(device.timestamps.phaseSince)?.let { "since $it" },
      ).joinToString(" · ")
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (device.paused) {
          InfoPill(text = "Paused")
        }
        if (device.nodeStatus.unschedulable == true) {
          InfoPill(text = "Cordoned")
        }
        Box(modifier = Modifier.weight(1f))

        if (pending) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
          FilledTonalIconButton(
            onClick = { onRequest(PowerAction.Start) },
            enabled = device.canStart,
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start ${device.name}")
          }
          FilledTonalIconButton(
            onClick = { onRequest(PowerAction.Stop) },
            enabled = device.canStop,
          ) {
            Icon(Icons.Default.Clear, contentDescription = "Stop ${device.name}")
          }
        }
      }
    }
  }
}
