package cloud.hauke.sandman.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.hauke.sandman.data.DeviceRepository
import cloud.hauke.sandman.data.Settings
import cloud.hauke.sandman.data.SettingsRepository
import cloud.hauke.sandman.data.remote.ApiConfig
import cloud.hauke.sandman.ui.UiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The result of the "test connection" button, which is the point of this screen. */
sealed interface ProbeResult {
  data object Idle : ProbeResult
  data object Running : ProbeResult
  data class Success(val deviceCount: Int) : ProbeResult
  data class Failure(val error: UiError) : ProbeResult
}

data class SettingsUiState(
  val settings: Settings = Settings(),
  val loaded: Boolean = false,
  val probe: ProbeResult = ProbeResult.Idle,
)

class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
  private val deviceRepository: DeviceRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      // Read once: the fields on this screen are edited locally and written
      // back on change, so following the store would fight the user's typing.
      val stored = settingsRepository.settings.first()
      _uiState.update { it.copy(settings = stored, loaded = true) }
    }
  }

  fun onBaseUrlChanged(value: String) = edit { it.copy(baseUrl = value) }

  fun onTokenChanged(value: String) = edit { it.copy(token = value) }

  fun onRequestedByChanged(value: String) = edit { it.copy(requestedBy = value) }

  fun onAutoRefreshChanged(value: Boolean) = edit { it.copy(autoRefresh = value) }

  fun onRefreshSecondsChanged(value: Int) = edit { it.copy(refreshSeconds = value) }

  fun onConfirmStopChanged(value: Boolean) = edit { it.copy(confirmStop = value) }

  private fun edit(transform: (Settings) -> Settings) {
    val updated = transform(_uiState.value.settings)
    _uiState.update { it.copy(settings = updated, probe = ProbeResult.Idle) }
    viewModelScope.launch { settingsRepository.update { updated } }
  }

  fun testConnection() {
    val settings = _uiState.value.settings
    viewModelScope.launch {
      _uiState.update { it.copy(probe = ProbeResult.Running) }
      val result = try {
        ProbeResult.Success(deviceRepository.probe(ApiConfig(settings.baseUrl, settings.token)))
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (throwable: Throwable) {
        ProbeResult.Failure(UiError.from(throwable))
      }
      _uiState.update { it.copy(probe = result) }
    }
  }
}
