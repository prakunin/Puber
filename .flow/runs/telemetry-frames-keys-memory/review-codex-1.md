CODEX: ran: review, adversarial (model gpt-6-astra, no fallback needed)

CONFIRMED:
- [major] app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt:23-24 (callers `FlowComponent.kt:271,751`) - After a back or pop to the Main screen, the screen holder stays on the wrong class and frames, key, key_lat and mem lines carry the wrong `scr`.
  - Scenario: the user opens Home, pushes Details, then presses Back to Home.
    - `recordNavCommand(event, navFrom, navigator.lastItemOrNull)` runs with the root navigator's top screen, `MainScreen`, so `Telemetry.screen = MainScreen`.
    - The tab is only re-announced by `TabComponent.kt:111` (`recordNav(TAB, ...)`) on `TabCommand.Open`, which does not fire on return.
    - Until the next nav or tab event, the `frames`, `key`, `key_lat` and `mem` lines say `MainScreen`, not Home.
    - This breaks AC-12 ("a `frames` line for that screen") on a common path. The same applies when `FinishFlow` or `Back` pops a parent navigator, because the runner reports its own navigator's top.
  - I confirmed the code path. Nothing else writes `Telemetry.screen`, and `Telemetry.screen` is only assigned at `NavTelemetry.kt:24` and cleared at `TelemetryRuntime.kt:106`. I did not run it on a device.
- [minor] app/src/main/java/com/kino/puber/core/telemetry/TelemetryProbe.kt:78-81 with `TelemetryRuntime.kt:64-68` - Turning the toggle off while an Activity is resumed drops the open frames window and the open key burst.
  - Scenario: the user turns the Session log toggle off from Settings.
    - `setEnabled(false)` calls `sink.stop()`, which sets `isEnabled = false` at once (`TelemetrySink.kt:70-73`).
    - It then calls `detachProbes()`, which posts `ActivityProbes.detach()` to the main thread.
    - There `frameProbe.stop()` and `keyDetach()` flush through `Telemetry.record`, which checks `isEnabled` and discards the events (`Telemetry.kt:39-42`).
    - The final window of up to 10 s and any open burst are lost. A short recording can lose all its frames.
  - The pause path does flush before the drain (`TelemetryActivityCallbacks.onActivityPaused`), so only toggle-off and the equivalent shutdown paths are affected. Codex rated it P2. I downgrade it because the loss is bounded to one window and one burst, and the log is still valid.

REJECTED:
- None. Both Codex findings held. The `review` run produced one finding and the adversarial pass produced one, with no overlap between them.

VERDICT: changes needed
