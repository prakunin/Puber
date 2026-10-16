package com.kino.puber.core.telemetry

import android.view.KeyEvent

internal const val UNCAPPED = Int.MAX_VALUE

private const val SESSION_CAP = 10
private const val STARTUP_CAP = 2
private const val NAV_CAP = 120
private const val READY_CAP = 240
private const val ACT_CAP = 240
private const val ERR_CAP = 20
private const val FRAMES_CAP = 12
private const val KEY_CAP = 120
private const val KEY_LAT_CAP = 60
private const val MEM_CAP = 10

/** Wire name and per-minute cap of each event type; a new event type declares its cap here. */
internal enum class TelemetryEventType(val wire: String, val capPerMinute: Int) {
    SESSION("session", SESSION_CAP),
    STARTUP("startup", STARTUP_CAP),
    NAV("nav", NAV_CAP),
    READY("ready", READY_CAP),
    ACT("act", ACT_CAP),
    ERR("err", ERR_CAP),
    FRAMES("frames", FRAMES_CAP),
    KEY("key", KEY_CAP),
    KEY_LAT("key_lat", KEY_LAT_CAP),
    MEM("mem", MEM_CAP),
    META("meta", UNCAPPED),
}

internal enum class NavCmd(val wire: String) {
    PUSH("push"),
    PUSH_RESULT("push_result"),
    REPLACE("replace"),
    NEW_ROOT("new_root"),
    BACK("back"),
    BACK_TO("back_to"),
    FINISH_FLOW("finish_flow"),
    TAB("tab"),
    ACTIVITY("activity"),
}

internal enum class ErrSource(val wire: String) {
    LOG("log"),
    AUTH("auth"),
    UNCAUGHT("uncaught"),
}

internal enum class MetaEv(val wire: String) {
    START("start"),
    STOP("stop"),
    ROTATE("rotate"),
    AUTO_OFF("auto_off"),
    DROPPED("dropped"),
    UNAVAILABLE("unavailable"),
}

/** The key codes a line may name. Every other code is [OTHER], so typed text can never be rebuilt. */
internal enum class KeyName(val wire: String) {
    DPAD_UP("dpad_up"),
    DPAD_DOWN("dpad_down"),
    DPAD_LEFT("dpad_left"),
    DPAD_RIGHT("dpad_right"),
    DPAD_CENTER("dpad_center"),
    ENTER("enter"),
    BACK("back"),
    MENU("menu"),
    MEDIA_PLAY_PAUSE("play_pause"),
    MEDIA_STOP("stop"),
    MEDIA_NEXT("next"),
    MEDIA_PREVIOUS("previous"),
    MEDIA_REWIND("rewind"),
    MEDIA_FAST_FORWARD("fast_forward"),
    MEDIA_PLAY("play"),
    MEDIA_PAUSE("pause"),
    CHANNEL_UP("channel_up"),
    CHANNEL_DOWN("channel_down"),
    PAGE_UP("page_up"),
    PAGE_DOWN("page_down"),
    MOVE_HOME("move_home"),
    MOVE_END("move_end"),
    OTHER("other"),
}

internal fun keyNameOf(code: Int): KeyName = when (code) {
    KeyEvent.KEYCODE_DPAD_UP -> KeyName.DPAD_UP
    KeyEvent.KEYCODE_DPAD_DOWN -> KeyName.DPAD_DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> KeyName.DPAD_LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> KeyName.DPAD_RIGHT
    KeyEvent.KEYCODE_DPAD_CENTER -> KeyName.DPAD_CENTER
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyName.ENTER
    KeyEvent.KEYCODE_BACK -> KeyName.BACK
    KeyEvent.KEYCODE_MENU -> KeyName.MENU
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> KeyName.MEDIA_PLAY_PAUSE
    KeyEvent.KEYCODE_MEDIA_STOP -> KeyName.MEDIA_STOP
    KeyEvent.KEYCODE_MEDIA_NEXT -> KeyName.MEDIA_NEXT
    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> KeyName.MEDIA_PREVIOUS
    KeyEvent.KEYCODE_MEDIA_REWIND -> KeyName.MEDIA_REWIND
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> KeyName.MEDIA_FAST_FORWARD
    KeyEvent.KEYCODE_MEDIA_PLAY -> KeyName.MEDIA_PLAY
    KeyEvent.KEYCODE_MEDIA_PAUSE -> KeyName.MEDIA_PAUSE
    KeyEvent.KEYCODE_CHANNEL_UP -> KeyName.CHANNEL_UP
    KeyEvent.KEYCODE_CHANNEL_DOWN -> KeyName.CHANNEL_DOWN
    KeyEvent.KEYCODE_PAGE_UP -> KeyName.PAGE_UP
    KeyEvent.KEYCODE_PAGE_DOWN -> KeyName.PAGE_DOWN
    KeyEvent.KEYCODE_MOVE_HOME -> KeyName.MOVE_HOME
    KeyEvent.KEYCODE_MOVE_END -> KeyName.MOVE_END
    else -> KeyName.OTHER
}

/** An event that carries the screen it was measured on; the writer uses it instead of its last nav. */
internal sealed interface ScreenScoped {
    val screen: Class<*>?
}

/**
 * Immutable and cheap to build on any thread: probes put only primitives, enums, `Class` and
 * `Throwable` references in an event. Names, JSON and IO happen on the writer thread.
 */
internal sealed interface TelemetryEvent {
    val t: Long
    val type: TelemetryEventType

    data class Session(override val t: Long) : TelemetryEvent {
        override val type get() = TelemetryEventType.SESSION
    }

    data class Startup(
        override val t: Long,
        val procStartMs: Long,
        val appCreateMs: Long,
        val appComposedMs: Long?,
        val firstContentMs: Long?,
    ) : TelemetryEvent {
        override val type get() = TelemetryEventType.STARTUP
    }

    data class Nav(
        override val t: Long,
        val cmd: NavCmd,
        val from: Class<*>?,
        val to: Class<*>?,
    ) : TelemetryEvent {
        override val type get() = TelemetryEventType.NAV
    }

    data class Ready(
        override val t: Long,
        val vm: Class<*>,
        val state: Class<*>,
        val sinceOpenMs: Long,
    ) : TelemetryEvent {
        override val type get() = TelemetryEventType.READY
    }

    data class Act(
        override val t: Long,
        val vm: Class<*>,
        val action: Class<*>,
        val n: Int = 1,
    ) : TelemetryEvent {
        override val type get() = TelemetryEventType.ACT
    }

    data class Err(
        override val t: Long,
        val src: ErrSource,
        val error: Throwable,
        val n: Int = 1,
    ) : TelemetryEvent {
        override val type get() = TelemetryEventType.ERR
    }

    /**
     * One window of frames on one screen. All millisecond values are whole ms (floor). The frame time
     * is the UI-thread work of a frame (`FrameData.frameDurationUiNanos`) only, never GPU or present
     * time, so the metric means the same on every API level.
     */
    data class Frames(
        override val t: Long,
        override val screen: Class<*>?,
        val frames: Int,
        val janky: Int,
        val p50: Int,
        val p90: Int,
        val p99: Int,
        val maxMs: Int,
    ) : TelemetryEvent, ScreenScoped {
        override val type get() = TelemetryEventType.FRAMES
    }

    /** One key press or one held burst: [n] DOWN events over [durMs]. */
    data class Key(
        override val t: Long,
        override val screen: Class<*>?,
        val key: KeyName,
        val n: Int,
        val durMs: Long,
    ) : TelemetryEvent, ScreenScoped {
        override val type get() = TelemetryEventType.KEY
    }

    /** A key whose first answering frame ended more than 50 ms after the key's event time. */
    data class KeyLat(
        override val t: Long,
        override val screen: Class<*>?,
        val key: KeyName,
        val latMs: Long,
        val frameMs: Long,
    ) : TelemetryEvent, ScreenScoped {
        override val type get() = TelemetryEventType.KEY_LAT
    }

    /** A memory sample; [pssKb] on periodic samples, [trim] (the level) on trim samples. */
    data class Mem(
        override val t: Long,
        override val screen: Class<*>?,
        val javaKb: Long,
        val javaMaxKb: Long,
        val nativeKb: Long,
        val pssKb: Long?,
        val trim: Int?,
    ) : TelemetryEvent, ScreenScoped {
        override val type get() = TelemetryEventType.MEM
    }

    data class Meta(
        override val t: Long,
        val ev: MetaEv,
        val counts: Map<TelemetryEventType, Int> = emptyMap(),
    ) : TelemetryEvent {
        override val type get() = TelemetryEventType.META
    }
}
