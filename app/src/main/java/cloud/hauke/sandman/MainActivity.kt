package cloud.hauke.sandman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cloud.hauke.sandman.ui.SandmanApp
import cloud.hauke.sandman.ui.theme.SandmanTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val container = (application as SandmanApplication).container
    setContent {
      SandmanTheme {
        SandmanApp(container)
      }
    }
  }
}
