package cloud.hauke.sandman.ui.detail

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

private const val IN_FLIGHT_POLL_MILLIS = 2_000L

data class DeviceDetailUiState(
  val deviceName: String = "",
  val settings: Settings = Settings(),
  val device: Device? = null,
  val loading: Boolean = false,
  val error: UiError? = null,
  val pending: Boolean = false,
)

/**
 * The one-device view. It polls the same way the list does, only faster while
 * the machine is moving: this is the screen someone stares at after pressing
 * start, waiting for the node to come back.
 */
class DeviceDetailViewModel(
  private val settingsRepository: SettingsRepository,
  private val deviceRepository: DeviceRepository,
  deviceName: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(DeviceDetailUiState(deviceName = deviceName))
  val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

  private val _messages = MutableSharedFlow<String>(
    extraBufferCapacity = 4,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private val visible = MutableStateFlow(true)
  private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

  init {
    viewModelScope.launch {
      settingsRepository.settings.collectLatest { settings ->
        _uiState.update { it.copy(settings = settings) }
        if (!settings.isConfigured) return@collectLatest
        pollLoop(settings)
      }
    }
  }

  private suspend fun pollLoop(settings: Settings) {
    while (viewModelScope.isActive) {
      visible.first { it }
      load(settings)

      val interval = when {
        _uiState.value.device?.inFlight == true -> IN_FLIGHT_POLL_MILLIS
        settings.autoRefresh -> settings.refreshSeconds * 1_000L
        else -> null
      }

      if (interval == null) {
        refreshRequests.receive()
      } else {
        withTimeoutOrNull(interval) { refreshRequests.receive() }
      }
    }
  }

  private suspend fun load(settings: Settings) {
    _uiState.update { it.copy(loading = true) }
    try {
      val device = deviceRepository.device(settings.apiConfig, _uiState.value.deviceName)
      _uiState.update { it.copy(device = device, loading = false, error = null) }
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

  fun start(reason: String?) = act(PowerAction.Start, reason)

  fun stop(reason: String?) = act(PowerAction.Stop, reason)

  fun release(reason: String?) = act(PowerAction.Release, reason)

  private fun act(action: PowerAction, reason: String?) {
    val name = _uiState.value.deviceName
    viewModelScope.launch {
      val settings = _uiState.value.settings
      _uiState.update { it.copy(pending = true) }
      try {
        val response = when (action) {
          PowerAction.Start -> deviceRepository.start(settings.apiConfig, name, settings, reason)
          PowerAction.Stop -> deviceRepository.stop(settings.apiConfig, name, settings, reason)
          PowerAction.Release -> deviceRepository.release(settings.apiConfig, name, settings, reason)
        }
        _messages.tryEmit(response.message.ifBlank { "Request accepted" })
        response.device?.let { device -> _uiState.update { it.copy(device = device) } }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (throwable: Throwable) {
        val error = UiError.from(throwable)
        _messages.tryEmit(listOfNotNull(error.message, error.hint).joinToString(" "))
      } finally {
        _uiState.update { it.copy(pending = false) }
        refresh()
      }
    }
  }
}
