LENS: edge  ROUND: 2  BASE: c506d0bd

No findings.

### Unproven
- `app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:110` (`reportable = begun || forcedCause != null`): this works only because the telemetry marks are set up before the activity starts, and that holds today. The restored-start report fires once inside `MainActivity.onCreate`, and `revealedOnce` blocks any second report. `TelemetryRuntime.install` (called from `PuberApp.onCreate`, `TelemetryRuntime.kt:162`) sets `Telemetry.startupMarks` synchronously before that point, so nothing is lost now. This would become a real problem if the marks were ever created asynchronously or after the activity's `onCreate`. In that case `startupMarks?.markRevealCause` would hit null and the cause would never be reported again.
