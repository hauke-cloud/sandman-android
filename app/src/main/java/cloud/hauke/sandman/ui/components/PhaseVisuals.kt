package cloud.hauke.sandman.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cloud.hauke.sandman.data.model.PowerPhase

/** How a phase reads at a glance: a colour, an icon and a word. */
data class PhaseVisuals(
  val label: String,
  val container: Color,
  val content: Color,
  val icon: ImageVector?,
)

private val Green = Color(0xFF1E8E3E)
private val GreenSoft = Color(0xFFD7F0DD)
private val Grey = Color(0xFF5F6368)
private val GreySoft = Color(0xFFE3E5E8)
private val Amber = Color(0xFF9A6A12)
private val AmberSoft = Color(0xFFFDEBC8)
private val Red = Color(0xFFB3261E)
private val RedSoft = Color(0xFFF9DEDC)

@Composable
fun PowerPhase.visuals(): PhaseVisuals = when (this) {
  PowerPhase.Started -> PhaseVisuals("Running", GreenSoft, Green, Icons.Default.Check)
  PowerPhase.Stopped -> PhaseVisuals("Stopped", GreySoft, Grey, Icons.Default.Clear)
  PowerPhase.Starting -> PhaseVisuals("Starting", AmberSoft, Amber, null)
  PowerPhase.Stopping -> PhaseVisuals("Stopping", AmberSoft, Amber, null)
  PowerPhase.Pending -> PhaseVisuals("Pending", AmberSoft, Amber, null)
  PowerPhase.Failed -> PhaseVisuals("Failed", RedSoft, Red, Icons.Default.Warning)
  PowerPhase.Unknown -> PhaseVisuals("Unknown", GreySoft, Grey, Icons.Default.Info)
}

/** The state pill shown on every device, in the list and on its own screen. */
@Composable
fun PhaseBadge(
  phase: PowerPhase,
  modifier: Modifier = Modifier,
) {
  val visuals = phase.visuals()
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = visuals.container,
    contentColor = visuals.content,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      when {
        phase.inFlight -> CircularProgressIndicator(
          modifier = Modifier.size(12.dp),
          strokeWidth = 2.dp,
          color = visuals.content,
        )

        visuals.icon != null -> Icon(
          imageVector = visuals.icon,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
      }
      Text(
        text = visuals.label,
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }
}

/** A small neutral pill for the extra facts: paused, unschedulable, and so on. */
@Composable
fun InfoPill(
  text: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = Icons.Default.Refresh,
  container: Color = MaterialTheme.colorScheme.secondaryContainer,
  content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = container,
    contentColor = content,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
      }
      Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
  }
}
