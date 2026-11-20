package dev.keystone.auto

import android.graphics.Bitmap
import dev.keystone.auto.core.Combined
import dev.keystone.auto.core.Offsets
import dev.keystone.auto.core.Params
import dev.keystone.auto.link.ProjectorApp
import dev.keystone.auto.link.ProjectorInfo
import dev.keystone.auto.link.ProjectorStatus

/** Where the user is in the flow. */
sealed interface Screen {
    data object Connect : Screen
    data object Home : Screen
    data object Remote : Screen
    data object Keyboard : Screen
    data object Apps : Screen
    data object Install : Screen
    data object Device : Screen
    data object Guide : Screen
    data object Capture : Screen
    data object Working : Screen
    data object Verify : Screen
    data object Feedback : Screen
    data object ResultPhoto : Screen
    data object Done : Screen
    data object Settings : Screen
    data object History : Screen
    data object Log : Screen
}

data class DeviceChoice(val host: String, val name: String, val supported: Boolean, val note: String?)

sealed interface ConnectState {
    data object Idle : ConnectState
    data class Scanning(val done: Int, val total: Int) : ConnectState
    data class Choose(val devices: List<DeviceChoice>) : ConnectState
    data class Connecting(val host: String, val step: String) : ConnectState
    data class Connected(val info: ProjectorInfo, val keystone: Offsets) : ConnectState
    data class Failed(val message: String, val hint: String? = null) : ConnectState
}

/** One photo in the capture round. */
class CaptureItem(
    val id: Int,
    val measurement: dev.keystone.auto.core.CaptureMeasurement?,
    val thumb: Bitmap?,
    val elevationDeg: Double,
    val rollDeg: Double,
    val problem: String?,
    val warning: String?,
) {
    val usable get() = measurement != null
}

data class Session(
    val keystoneAtStart: Offsets,
    val target: Int,
    val items: List<CaptureItem> = emptyList(),
) {
    val usableCount get() = items.count { it.usable }
}

data class Work(val message: String, val fraction: Float)

data class VerifyState(
    val before: Offsets,          // the keystone before this session (for undo)
    val applied: Offsets,         // what is on the projector now
    val combined: Combined,
    val params: Params,
    val round: Int,
    val lastFeedback: String? = null,
)

data class DoneState(
    val before: Offsets,
    val after: Offsets,
    val learned: Params,
    val resultPhotoUsed: Boolean,
    val acceptedTotal: Int,
)

/** The phone's mouse/keyboard on the projector. */
sealed interface RemoteState {
    data object Idle : RemoteState
    data object Starting : RemoteState
    data object Ready : RemoteState
    data class Failed(val message: String) : RemoteState
}

sealed interface AppsState {
    data object Loading : AppsState
    data class Loaded(val apps: List<ProjectorApp>) : AppsState
    data class Failed(val message: String) : AppsState
}

sealed interface InstallState {
    data object Idle : InstallState
    data class Working(val name: String, val step: String) : InstallState
    data class Done(val name: String, val ok: Boolean, val message: String) : InstallState
}

/** What the Projector screen shows. */
data class DeviceState(val status: ProjectorStatus?, val firmwareAutoKeystone: Boolean?, val loading: Boolean = false, val customLauncher: Boolean = false)
