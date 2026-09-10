package cloud.hauke.sandman.ui

import android.util.Log
import cloud.hauke.sandman.data.remote.SandmanException

/** An error in the two lines the UI has room for. */
data class UiError(
  val message: String,
  val hint: String? = null,
  val requiresToken: Boolean = false,
) {
  companion object {
    fun from(throwable: Throwable): UiError = when (throwable) {
      is SandmanException -> UiError(
        message = throwable.displayMessage,
        hint = throwable.hint,
        requiresToken = throwable is SandmanException.Api && throwable.isUnauthorized,
      )

      // Nothing should reach here: SandmanClient turns every expected
      // failure into a SandmanException. Anything else is a bug, and a bug
      // the user cannot describe is a bug nobody can fix.
      else -> {
        Log.e("sandman", "unexpected failure", throwable)
        UiError(message = throwable.message ?: "Something went wrong")
      }
    }
  }
}
