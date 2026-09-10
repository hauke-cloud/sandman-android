package cloud.hauke.sandman.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver

/**
 * Tells a view model whether its screen is in front of anyone.
 *
 * Polling a fleet of machines from a phone in a pocket is a way to flatten a
 * battery for nothing, so the loops stop at ON_PAUSE and pick up again at
 * ON_RESUME.
 */
@Composable
fun ReportVisibility(onVisibilityChanged: (Boolean) -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val callback by rememberUpdatedState(onVisibilityChanged)

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> callback(true)
        Lifecycle.Event.ON_PAUSE -> callback(false)
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      callback(false)
    }
  }
}
