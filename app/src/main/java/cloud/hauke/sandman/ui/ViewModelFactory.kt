package cloud.hauke.sandman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cloud.hauke.sandman.AppContainer
import cloud.hauke.sandman.ui.detail.DeviceDetailViewModel
import cloud.hauke.sandman.ui.devices.DeviceListViewModel
import cloud.hauke.sandman.ui.settings.SettingsViewModel

/**
 * One factory for the three view models. [deviceName] is only read by the
 * detail screen, which is why it is a property of the factory rather than
 * something plumbed through a saved-state handle.
 */
class SandmanViewModelFactory(
  private val container: AppContainer,
  private val deviceName: String = "",
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
    modelClass.isAssignableFrom(DeviceListViewModel::class.java) ->
      DeviceListViewModel(container.settingsRepository, container.deviceRepository) as T

    modelClass.isAssignableFrom(DeviceDetailViewModel::class.java) ->
      DeviceDetailViewModel(container.settingsRepository, container.deviceRepository, deviceName) as T

    modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
      SettingsViewModel(container.settingsRepository, container.deviceRepository) as T

    else -> throw IllegalArgumentException("unknown view model ${modelClass.name}")
  }
}
