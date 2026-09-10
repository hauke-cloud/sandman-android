package cloud.hauke.sandman.ui.components

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val absoluteFormatter: DateTimeFormatter =
  DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** Parses one of sandman's RFC 3339 timestamps, or null if it is not one. */
fun parseTimestamp(value: String?): Instant? =
  value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** "4 minutes ago" -- what an operator actually wants to know about a phase. */
fun relativeTime(value: String?, now: Instant = Instant.now()): String? {
  val instant = parseTimestamp(value) ?: return null
  val elapsed = Duration.between(instant, now)
  if (elapsed.isNegative) return "just now"

  val seconds = elapsed.seconds
  return when {
    seconds < 45 -> "just now"
    seconds < 90 -> "a minute ago"
    seconds < 3600 -> "${elapsed.toMinutes()} minutes ago"
    seconds < 7200 -> "an hour ago"
    seconds < 86_400 -> "${elapsed.toHours()} hours ago"
    seconds < 172_800 -> "yesterday"
    else -> "${elapsed.toDays()} days ago"
  }
}

/** The same instant spelled out, for the detail screen where precision helps. */
fun absoluteTime(value: String?): String? =
  parseTimestamp(value)
    ?.atZone(ZoneId.systemDefault())
    ?.format(absoluteFormatter)
