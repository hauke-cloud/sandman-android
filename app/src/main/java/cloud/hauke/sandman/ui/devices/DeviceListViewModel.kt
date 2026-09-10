package cloud.hauke.sandman.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.hauke.sandman.data.DeviceRepository
import cloud.hauke.sandman.data.Settings
import cloud.hauke.sandman.data.SettingsRepository
import cloud.hauke.sandman.data.model.Device
import cloud.hauke.sandman.data.remote.PowerAction
import cloud.hauke.sandman.ui.UiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** While anything is in flight the fleet is changing, so it is polled harder. */
private const val IN_FLIGHT_POLL_MILLIS = 3_000L

data class DeviceListUiState(
  val settings: Settings = Settings(),
  val devices: List<Device> = emptyList(),
  val summary: Map<String, Int> = emptyMap(),
  val loading: Boolean = false,
  val error: UiError? = null,
  /** Names of devices whose start or stop has been sent but not yet answered. */
  val pending: Set<String> = emptySet(),
  val loadedAtLeastOnce: Boolean = false,
) {
  val isConfigured: Boolean get() = settings.isConfigured
  val anyInFlight: Boolean get() = devices.any { it.inFlight } || pending.isNotEmpty()
}

class DeviceListViewModel(
  private val settingsRepository: SettingsRepository,
  private val deviceRepository: DeviceRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(DeviceListUiState())
  val uiState: StateFlow<DeviceListUiState> = _uiState.asStateFlow()

  private val _messages = MutableSharedFlow<String>(
    extraBufferCapacity = 4,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  /** Set by the screen: polling stops when the app is not in front of anyone. */
  private val visible = MutableStateFlow(true)

  // A conflated channel rather than a flow: a refresh asked for while a load
  // is already running has to survive until the loop comes back round to it.
  private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

  init {
    viewModelScope.launch {
      // collectLatest, so that changing the server address abandons the poll
      // against the old one instead of interleaving with it.
      settingsRepository.settings.collectLatest { settings ->
        _uiState.update { it.copy(settings = settings) }
        if (!settings.isConfigured) {
          _uiState.update {
            it.copy(devices = emptyList(), summary = emptyMap(), loading = false, error = null)
          }
          return@collectLatest
        }
        pollLoop(settings)
      }
    }
  }

  private suspend fun pollLoop(settings: Settings) {
    while (viewModelScope.isActive) {
      visible.first { it }
      load(settings)

      val interval = when {
        _uiState.value.anyInFlight -> IN_FLIGHT_POLL_MILLIS
        settings.autoRefresh -> settings.refreshSeconds * 1_000L
        else -> null
      }

      if (interval == null) {
        // Nothing scheduled: wait for the user to ask.
        refreshRequests.receive()
      } else {
        withTimeoutOrNull(interval) { refreshRequests.receive() }
      }
    }
  }

  private suspend fun load(settings: Settings) {
    _uiState.update { it.copy(loading = true) }
    try {
      val result = deviceRepository.list(settings.apiConfig)
      _uiState.update {
        it.copy(
          devices = result.items,
          summary = result.summary,
          loading = false,
          error = null,
          loadedAtLeastOnce = true,
        )
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (throwable: Throwable) {
      _uiState.update { it.copy(loading = false, error = UiError.from(throwable)) }
    }
  }

  fun onVisibilityChanged(isVisible: Boolean) {
    visible.value = isVisible
  }

  fun refresh() {
    refreshRequests.trySend(Unit)
  }

  fun start(device: Device, reason: String?) = act(device, PowerAction.Start, reason)

  fun stop(device: Device, reason: String?) = act(device, PowerAction.Stop, reason)

  fun release(device: Device, reason: String?) = act(device, PowerAction.Release, reason)

  private fun act(device: Device, action: PowerAction, reason: String?) {
    viewModelScope.launch {
      val settings = _uiState.value.settings
      _uiState.update { it.copy(pending = it.pending + device.name) }
      try {
        val response = when (action) {
          PowerAction.Start -> deviceRepository.start(settings.apiConfig, device.name, settings, reason)
          PowerAction.Stop -> deviceRepository.stop(settings.apiConfig, device.name, settings, reason)
          PowerAction.Release -> deviceRepository.release(settings.apiConfig, device.name, settings, reason)
        }
        _messages.tryEmit(response.message.ifBlank { "Request accepted" })
        // The device sandman hands back already carries the new desired state,
        // so the row updates before the next poll comes round.
        response.device?.let { updated -> replace(updated) }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (throwable: Throwable) {
        val error = UiError.from(throwable)
        _messages.tryEmit(listOfNotNull(error.message, error.hint).joinToString(" "))
      } finally {
        _uiState.update { it.copy(pending = it.pending - device.name) }
        refresh()
      }
    }
  }

  private fun replace(device: Device) {
    _uiState.update { state ->
      state.copy(devices = state.devices.map { if (it.name == device.name) device else it })
    }
  }
}
