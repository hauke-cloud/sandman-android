package cloud.hauke.sandman.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The state an operator asks a machine to be in.
 *
 * Mirrors `v1alpha1.PowerState`.
 */
@Serializable
enum class PowerState {
  @SerialName("On")
  On,

  @SerialName("Off")
  Off,

  @SerialName("Unmanaged")
  Unmanaged,
}

/**
 * The observed state of a machine.
 *
 * Mirrors `v1alpha1.PowerPhase`. An unrecognised value from a newer sandman
 * decodes as [Unknown] rather than failing the whole response.
 */
@Serializable
enum class PowerPhase {
  @SerialName("Unknown")
  Unknown,

  @SerialName("Pending")
  Pending,

  @SerialName("Starting")
  Starting,

  @SerialName("Started")
  Started,

  @SerialName("Stopping")
  Stopping,

  @SerialName("Stopped")
  Stopped,

  @SerialName("Failed")
  Failed;

  /** True while the machine is between states, which is the signal to poll. */
  val inFlight: Boolean
    get() = this == Pending || this == Starting || this == Stopping
}

/** What the cluster currently sees of the node. */
@Serializable
data class NodeStatus(
  val found: Boolean = false,
  val ready: Boolean? = null,
  val unschedulable: Boolean? = null,
)

/** When the device last did each thing. RFC 3339, as sandman writes them. */
@Serializable
data class Timestamps(
  val phaseSince: String? = null,
  val lastWake: String? = null,
  val lastShutdown: String? = null,
  val lastStarted: String? = null,
  val lastStopped: String? = null,
)

/** A Kubernetes condition, passed through by the API. */
@Serializable
data class Condition(
  val type: String = "",
  val status: String = "",
  val reason: String? = null,
  val message: String? = null,
  val lastTransitionTime: String? = null,
  val observedGeneration: Long? = null,
)

/** The API's view of a SandmanDevice. */
@Serializable
data class Device(
  val name: String,
  val node: String = "",
  val description: String? = null,
  val macAddress: String = "",
  val broadcastAddress: String = "",
  val address: String? = null,
  val state: PowerPhase = PowerPhase.Unknown,
  val desiredState: PowerState = PowerState.Unmanaged,
  val inFlight: Boolean = false,
  val reason: String? = null,
  val message: String? = null,
  val paused: Boolean = false,
  val nodeStatus: NodeStatus = NodeStatus(),
  val timestamps: Timestamps = Timestamps(),
  val wakeAttempts: Int = 0,
  val shutdownPod: String? = null,
  val conditions: List<Condition> = emptyList(),
) {
  /**
   * Whether a start would change anything. A device already on, or already on
   * its way, does not need waking again.
   */
  val canStart: Boolean
    get() = !inFlight && state != PowerPhase.Started

  val canStop: Boolean
    get() = !inFlight && state != PowerPhase.Stopped
}

/** The response of the collection endpoints. */
@Serializable
data class DeviceList(
  val items: List<Device> = emptyList(),
  val count: Int = 0,
  // Keyed by phase name. Kept as strings so a phase this app does not know
  // about is carried rather than rejected.
  val summary: Map<String, Int> = emptyMap(),
)

/**
 * The optional body of a start or stop call. Both fields are for the record
 * only: they change what sandman can tell you afterwards, not what it does.
 */
@Serializable
data class PowerRequest(
  val requestedBy: String? = null,
  val reason: String? = null,
)

/** Returned by a start, stop or release call. */
@Serializable
data class PowerResponse(
  val accepted: Boolean = false,
  @SerialName("requestID")
  val requestId: String = "",
  val device: Device? = null,
  val message: String = "",
)

/** The body of every failed request. */
@Serializable
data class ApiError(
  val error: String = "",
  val message: String = "",
  val details: String? = null,
)
