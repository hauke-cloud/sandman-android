package cloud.hauke.sandman.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The middle of an otherwise empty screen: what happened, and what to do. */
@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(48.dp),
      tint = MaterialTheme.colorScheme.outline,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 16.dp),
      textAlign = TextAlign.Center,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp),
    )
    if (actionLabel != null && onAction != null) {
      Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) {
        Text(actionLabel)
      }
    }
  }
}
