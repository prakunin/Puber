package com.kino.puber.core.telemetry

internal const val UNCAPPED = Int.MAX_VALUE

private const val SESSION_CAP = 10
private const val STARTUP_CAP = 2
private const val NAV_CAP = 120
private const val READY_CAP = 240
private const val ACT_CAP = 240
private const val ERR_CAP = 20

/** Wire name and per-minute cap of each event type; a new event type declares its cap here. */
internal enum class TelemetryEventType(val wire: String, val capPerMinute: Int) {
    SESSION("session", SESSION_CAP),
    STARTUP("startup", STARTUP_CAP),
    NAV("nav", NAV_CAP),
    READY("ready", READY_CAP),
    ACT("act", ACT_CAP),
    ERR("err", ERR_CAP),
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

    data class Meta(
        override val t: Long,
        val ev: MetaEv,
        val counts: Map<TelemetryEventType, Int> = emptyMap(),
    ) : TelemetryEvent {
        override val type get() = TelemetryEventType.META
    }
}
