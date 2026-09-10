package cloud.hauke.sandman.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cloud.hauke.sandman.data.remote.ApiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the app needs to know that the user gets to decide. */
data class Settings(
  val baseUrl: String = "",
  val token: String = "",
  /** Recorded on every power request, so the audit trail names a person. */
  val requestedBy: String = "",
  val autoRefresh: Boolean = true,
  val refreshSeconds: Int = DEFAULT_REFRESH_SECONDS,
  /** Ask before a stop. Stopping a node is the one action worth a second look. */
  val confirmStop: Boolean = true,
) {
  val apiConfig: ApiConfig
    get() = ApiConfig(baseUrl = baseUrl, token = token)

  val isConfigured: Boolean
    get() = baseUrl.isNotBlank()

  companion object {
    const val DEFAULT_REFRESH_SECONDS = 15
    val REFRESH_CHOICES = listOf(5, 10, 15, 30, 60)
  }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sandman_settings")

class SettingsRepository(private val context: Context) {

  private object Keys {
    val BaseUrl = stringPreferencesKey("base_url")
    val Token = stringPreferencesKey("token")
    val RequestedBy = stringPreferencesKey("requested_by")
    val AutoRefresh = booleanPreferencesKey("auto_refresh")
    val RefreshSeconds = intPreferencesKey("refresh_seconds")
    val ConfirmStop = booleanPreferencesKey("confirm_stop")
  }

  val settings: Flow<Settings> = context.dataStore.data.map { preferences ->
    Settings(
      baseUrl = preferences[Keys.BaseUrl].orEmpty(),
      token = preferences[Keys.Token].orEmpty(),
      requestedBy = preferences[Keys.RequestedBy].orEmpty(),
      autoRefresh = preferences[Keys.AutoRefresh] ?: true,
      refreshSeconds = preferences[Keys.RefreshSeconds] ?: Settings.DEFAULT_REFRESH_SECONDS,
      confirmStop = preferences[Keys.ConfirmStop] ?: true,
    )
  }

  suspend fun update(transform: (Settings) -> Settings) {
    context.dataStore.edit { preferences ->
      val current = Settings(
        baseUrl = preferences[Keys.BaseUrl].orEmpty(),
        token = preferences[Keys.Token].orEmpty(),
        requestedBy = preferences[Keys.RequestedBy].orEmpty(),
        autoRefresh = preferences[Keys.AutoRefresh] ?: true,
        refreshSeconds = preferences[Keys.RefreshSeconds] ?: Settings.DEFAULT_REFRESH_SECONDS,
        confirmStop = preferences[Keys.ConfirmStop] ?: true,
      )
      val updated = transform(current)
      preferences[Keys.BaseUrl] = updated.baseUrl.trim()
      preferences[Keys.Token] = updated.token.trim()
      preferences[Keys.RequestedBy] = updated.requestedBy.trim()
      preferences[Keys.AutoRefresh] = updated.autoRefresh
      preferences[Keys.RefreshSeconds] = updated.refreshSeconds
      preferences[Keys.ConfirmStop] = updated.confirmStop
    }
  }
}
