package cloud.hauke.sandman

import android.app.Application
import android.content.Context
import android.os.StrictMode
import cloud.hauke.sandman.data.DeviceRepository
import cloud.hauke.sandman.data.SettingsRepository
import cloud.hauke.sandman.data.remote.SandmanClient
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * The app's dependencies, built once and handed out by hand.
 *
 * Three screens and one HTTP client do not need a container framework, and the
 * wiring is short enough to read in one go.
 */
class AppContainer(context: Context) {

  private val json = Json {
    ignoreUnknownKeys = true
    // A phase or state from a newer sandman falls back to the property's
    // default instead of failing the response.
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
  }

  private val httpClient: OkHttpClient = OkHttpClient.Builder()
    // A lab machine on the far side of a VPN can be slow to answer; a phone
    // that has wandered off the network should not hang for a minute.
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)

  val deviceRepository: DeviceRepository = DeviceRepository(SandmanClient(httpClient, json))
}

class SandmanApplication : Application() {

  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    enableStrictModeInDebug()
    container = AppContainer(this)
  }

  /**
   * Blocking work on the main thread is the one mistake this app is shaped to
   * make -- it is almost all network calls behind a coroutine. Android throws
   * on a main-thread socket read anyway; this catches the quieter cousins
   * (disk reads, leaked closeables) while there is still someone watching.
   */
  private fun enableStrictModeInDebug() {
    if (!BuildConfig.DEBUG) return

    StrictMode.setThreadPolicy(
      StrictMode.ThreadPolicy.Builder()
        .detectAll()
        .penaltyLog()
        .build(),
    )
    StrictMode.setVmPolicy(
      StrictMode.VmPolicy.Builder()
        .detectLeakedClosableObjects()
        .detectLeakedSqlLiteObjects()
        .penaltyLog()
        .build(),
    )
  }
}
