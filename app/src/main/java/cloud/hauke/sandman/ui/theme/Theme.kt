package cloud.hauke.sandman.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Midnight = Color(0xFF0F1626)
private val MoonBlue = Color(0xFF4C7DF0)
private val MoonBlueLight = Color(0xFF8AB4F8)
private val Amber = Color(0xFFF8C471)

private val LightColors = lightColorScheme(
  primary = MoonBlue,
  onPrimary = Color.White,
  secondary = Midnight,
  tertiary = Color(0xFF9A6A12),
)

private val DarkColors = darkColorScheme(
  primary = MoonBlueLight,
  onPrimary = Midnight,
  secondary = Color(0xFFB9C6E4),
  tertiary = Amber,
)

@Composable
fun SandmanTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Material You where the phone offers it: this is a tool that should look
  // like it belongs to the device it runs on.
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    darkTheme -> DarkColors
    else -> LightColors
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography(),
    content = content,
  )
}
