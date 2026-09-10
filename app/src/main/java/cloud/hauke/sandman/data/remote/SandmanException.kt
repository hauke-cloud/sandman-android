package cloud.hauke.sandman.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Everything that can go wrong between the app and sandman, in the shape the
 * UI wants to show it: a sentence a person can act on, and a hint when there
 * is a specific thing to do about it.
 */
sealed class SandmanException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause) {

  /** What to show the user. */
  abstract val displayMessage: String

  /** A second line, when there is something more to say. May be null. */
  open val hint: String? = null

  /** The address or token is missing or malformed. */
  class Configuration(override val displayMessage: String) : SandmanException(displayMessage) {
    override val hint: String = "Check the server address in Settings."
  }

  /** The request never reached sandman. */
  class Network(cause: IOException) : SandmanException(cause.message ?: "network error", cause) {
    override val displayMessage: String = when (cause) {
      is UnknownHostException -> "Cannot resolve that host"
      is ConnectException -> "Cannot reach sandman"
      is SocketTimeoutException -> "sandman did not answer in time"
      else -> cause.message ?: "The request failed"
    }

    override val hint: String =
      "Check the address, and that this device is on a network that can reach it."
  }

  /** sandman answered, but not with something this app understands. */
  class Protocol(
    override val displayMessage: String,
    cause: Throwable?,
  ) : SandmanException(displayMessage, cause)

  /** sandman answered with an error of its own. */
  class Api(
    val status: Int,
    val code: String,
    override val displayMessage: String,
    val details: String?,
  ) : SandmanException(displayMessage) {

    override val hint: String? = when (code) {
      "unauthorized" -> "Set or correct the bearer token in Settings."
      "read_only" -> "This sandman instance is running read-only."
      "shutdown_disabled" -> "The device has spec.shutdown.mode=Disabled."
      "ambiguous_node" -> details
      "conflict" -> "The device changed while the request was being written. Try again."
      "unavailable" -> "The Kubernetes API is not answering right now."
      else -> details
    }

    val isUnauthorized: Boolean get() = status == 401
  }
}
