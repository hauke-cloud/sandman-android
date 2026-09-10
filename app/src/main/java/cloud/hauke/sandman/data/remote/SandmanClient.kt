package cloud.hauke.sandman.data.remote

import cloud.hauke.sandman.data.model.ApiError
import cloud.hauke.sandman.data.model.Device
import cloud.hauke.sandman.data.model.DeviceList
import cloud.hauke.sandman.data.model.PowerRequest
import cloud.hauke.sandman.data.model.PowerResponse
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Where a sandman instance lives and how to authenticate against it. */
data class ApiConfig(
  val baseUrl: String,
  val token: String,
) {
  val isConfigured: Boolean
    get() = baseUrl.isNotBlank()
}

/** The power verbs the API exposes for a device. */
enum class PowerAction(val path: String) {
  Start("start"),
  Stop("stop"),
  Release("release"),
}

/**
 * A thin client over sandman's REST API.
 *
 * The base URL is a per-call argument rather than something baked in at
 * construction, because the address of the instance is a setting the user can
 * change at any moment and one client should not outlive it.
 */
class SandmanClient(
  private val httpClient: OkHttpClient,
  private val json: Json,
) {

  suspend fun listDevices(config: ApiConfig): DeviceList =
    get(config, DeviceList.serializer(), "api", "v1", "devices")

  suspend fun getDevice(config: ApiConfig, name: String): Device =
    get(config, Device.serializer(), "api", "v1", "devices", name)

  suspend fun power(
    config: ApiConfig,
    name: String,
    action: PowerAction,
    request: PowerRequest,
  ): PowerResponse {
    // sandman decodes the body with DisallowUnknownFields and treats an empty
    // one as valid, so only the fields it knows are ever sent.
    val body = json.encodeToString(PowerRequest.serializer(), request)
      .toRequestBody("application/json; charset=utf-8".toMediaType())

    val call = Request.Builder()
      .url(url(config, "api", "v1", "devices", name, action.path))
      .post(body)
      .authenticated(config)
      .build()

    return execute(call, PowerResponse.serializer())
  }

  /** A cheap round trip used by the settings screen to prove the setup works. */
  suspend fun probe(config: ApiConfig): Int = listDevices(config).count

  private suspend fun <T> get(
    config: ApiConfig,
    serializer: kotlinx.serialization.KSerializer<T>,
    vararg segments: String,
  ): T {
    val request = Request.Builder()
      .url(url(config, *segments))
      .get()
      .authenticated(config)
      .build()
    return execute(request, serializer)
  }

  private fun Request.Builder.authenticated(config: ApiConfig): Request.Builder = apply {
    header("Accept", "application/json")
    if (config.token.isNotBlank()) {
      header("Authorization", "Bearer ${config.token}")
    }
  }

  private fun url(config: ApiConfig, vararg segments: String): HttpUrl {
    val base = normalizeBaseUrl(config.baseUrl)
      ?: throw SandmanException.Configuration(
        "\"${config.baseUrl}\" is not a valid address. Use something like http://sandman.lab:8080",
      )
    return base.newBuilder()
      .apply { segments.forEach { addPathSegment(it) } }
      .build()
  }

  // On IO because reading the response body pulls from the socket: the call
  // itself is asynchronous, but the coroutine resumes on whichever dispatcher
  // asked for it, and on Android that is usually the main thread.
  private suspend fun <T> execute(
    request: Request,
    serializer: kotlinx.serialization.KSerializer<T>,
  ): T = withContext(Dispatchers.IO) {
    httpClient.newCall(request).await().use { response ->
      val body = response.body.string()
      if (!response.isSuccessful) {
        throw response.toException(body)
      }
      try {
        json.decodeFromString(serializer, body)
      } catch (cause: Exception) {
        throw SandmanException.Protocol(
          "sandman answered with something this app could not read",
          cause,
        )
      }
    }
  }

  private fun Response.toException(body: String): SandmanException {
    // Every failure sandman produces itself carries the Error envelope. A
    // proxy in front of it may not, so the status code is the fallback.
    val parsed = runCatching { json.decodeFromString(ApiError.serializer(), body) }.getOrNull()
    return SandmanException.Api(
      status = code,
      code = parsed?.error.orEmpty(),
      displayMessage = parsed?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(code),
      details = parsed?.details,
    )
  }

  private fun defaultMessage(status: Int): String = when (status) {
    401 -> "sandman rejected the token"
    403 -> "sandman refused the request"
    404 -> "sandman does not know about this device"
    409 -> "sandman could not act on the device in its current state"
    503 -> "sandman is not ready to answer yet"
    else -> "sandman answered with HTTP $status"
  }

  companion object {
    /**
     * Accepts what a person would type. A bare host gets an http scheme, a
     * trailing slash is harmless, and a path is kept so that an instance
     * behind a reverse proxy sub-path still works.
     */
    fun normalizeBaseUrl(raw: String): HttpUrl? {
      val trimmed = raw.trim().trimEnd('/')
      if (trimmed.isEmpty()) return null
      val withScheme =
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
      return withScheme.toHttpUrlOrNull()
    }
  }
}

/** Suspending bridge over OkHttp's callback API, cancelling the call with the coroutine. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
  continuation.invokeOnCancellation { runCatching { cancel() } }
  enqueue(object : Callback {
    override fun onFailure(call: Call, e: IOException) {
      if (continuation.isCancelled) return
      continuation.resumeWithException(SandmanException.Network(e))
    }

    override fun onResponse(call: Call, response: Response) {
      continuation.resume(response)
    }
  })
}
