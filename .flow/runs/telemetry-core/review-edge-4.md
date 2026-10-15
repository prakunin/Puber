LENS: edge  ROUND: 4 (bounded re-check of 4ece367c..9e0dad08)  BASE: 39903d31

No findings.

- One clock everywhere: TelemetryRuntime.install (TelemetryRuntime.kt:110-121) gives SystemTelemetryClock (elapsedRealtime) to the sink, Telemetry.install and StartupMarks; Telemetry.install runs before runtime.start. JvmTelemetryClock only applies while DisabledRecorder is installed.
- Startup marks: `t` set at emit, after start() took the origin; procStartMs/appCreateMs are fields, not `t`.
- Folded err/act: folding happens after the drainQueue filter; a new writer and folder per session.
- Ready: `t = now`; telemetryOpenedAtMs only feeds the duration.
- An event stamped just before start() took the origin happened before recording began; dropping it is correct.
- Concurrency: originElapsedMs read/written only on the writer thread; export drains through the same filter.
- Startup tabs: every option of getStartupTabOptions reaches a marked state (Home/Bookmarks/Collections Content; Favourites/History Empty or Content; content-list tabs SectionState.Content, which SectionVM also uses for Data/LoadingNext/Error/PageErrorNext/Refreshing; Auth). StartupTabContentStateTest enumerates the real repository options.
- Considered and rejected: a content-list startup tab where every section is genuinely empty falls back to the 60 s timeout with null; deliberate trade-off because SectionVM.kt:106 emits SectionState.Empty for the paginator's initial replay.
