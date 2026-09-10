package cloud.hauke.sandman.data

import android.os.Build
import cloud.hauke.sandman.data.model.Device
import cloud.hauke.sandman.data.model.DeviceList
import cloud.hauke.sandman.data.model.PowerRequest
import cloud.hauke.sandman.data.model.PowerResponse
import cloud.hauke.sandman.data.remote.ApiConfig
import cloud.hauke.sandman.data.remote.PowerAction
import cloud.hauke.sandman.data.remote.SandmanClient

/**
 * The app's way in to sandman.
 *
 * It exists mostly to attach the "who asked" fields to every power request:
 * sandman records them on the device, and a request that arrives with nobody's
 * name on it is worth less later than one that does.
 */
class DeviceRepository(private val client: SandmanClient) {

  suspend fun list(config: ApiConfig): DeviceList = client.listDevices(config)

  suspend fun device(config: ApiConfig, name: String): Device = client.getDevice(config, name)

  suspend fun probe(config: ApiConfig): Int = client.probe(config)

  suspend fun start(config: ApiConfig, name: String, settings: Settings, reason: String?) =
    power(config, name, PowerAction.Start, settings, reason)

  suspend fun stop(config: ApiConfig, name: String, settings: Settings, reason: String?) =
    power(config, name, PowerAction.Stop, settings, reason)

  suspend fun release(config: ApiConfig, name: String, settings: Settings, reason: String?) =
    power(config, name, PowerAction.Release, settings, reason)

  private suspend fun power(
    config: ApiConfig,
    name: String,
    action: PowerAction,
    settings: Settings,
    reason: String?,
  ): PowerResponse = client.power(
    config = config,
    name = name,
    action = action,
    request = PowerRequest(
      requestedBy = settings.requestedBy.ifBlank { defaultRequestedBy() },
      reason = reason?.trim()?.takeIf { it.isNotEmpty() },
    ),
  )

  /** Better than nothing in the annotation: the phone that sent it. */
  private fun defaultRequestedBy(): String = "sandman-android (${Build.MODEL})"
}
