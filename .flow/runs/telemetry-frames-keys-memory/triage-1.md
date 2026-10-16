# Review triage - round 1

Run: telemetry-frames-keys-memory   Base: cd16eb47   Reviewed head: 284bd7a0

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial). No review_extra: the diff touches no Compose layout and the run has no ui track.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex, edge F1 | major | `core/ui/navigation/component/FlowComponent.kt:271`, `NavTelemetry.kt:24` | Back / FinishFlow that pops a parent navigator records the runner's own (unchanged) top, so `Telemetry.screen` holds the screen just left (e.g. DeviceSettings after Back from Settings) and every frames/key/key_lat/mem line is mislabelled | Read `onBackWithNavigator` (recurses into `parent` when `!canPop()`) and line 271 (`navigator.lastItemOrNull` of the runner); scenario holds. Breaks the D3 "must not happen" case and AC-12 | fix | pending: record the top of the navigator that actually changed; unit test with a one-item child stack popping its parent |
| 2 | edge F2 | minor | `NavTelemetry.kt:23-24`, `TabComponent.kt:111` | After a root Back to the tab host, the holder is the container (`MainScreen`) instead of the selected tab's content (Home); per-screen stats split between Main and Home (seen in D12) | Read `recordNav` / `telemetryClassOf`; tab path writes the tab content class, flow path writes the top screen class; holds | fix | pending: when the new top is the tab host, write its current tab's content class |
| 3 | codex minor, blind F1 | minor | `TelemetryRuntime.kt:64-68`, `TelemetryProbe.kt:78-81` | Toggle-off sets the sink off before the posted detach flushes, so the last frames window and open key burst are discarded | Read `setEnabled(false)`: `sink.stop()` then `detachProbes()` posting detach; `Telemetry.record` drops when disabled; holds | fix (toggle path) / defer (writer-thread paths) | pending for the UI toggle: flush probes on main before the sink stops. Expiry and storage-failure paths: new run telemetry-expiry-flush-loss |
| 4 | blind F2 | minor | `TelemetryRuntime.kt:106`, `NavTelemetry.kt:24` | After enabling mid-session lines carry `scr ""` until the next navigation | Read the code; behaviour matches spec D3 | reject | Owner-approved design at gate 1: spec D3 and the "rejected alternatives" list choose `scr ""` until the next nav over a stale name, and test F13 pins it. With #1 fixed, Back from Settings now sets the right screen |
| 5 | blind F3 | minor | `TelemetryRuntime.kt:88-108`, `TelemetrySink.kt expire()` | Expiry racing a user re-enable detaches the new session's probes and removes `enabled_at` | Read `expire()` -> `onExpired()` ordering; race window real; tree/handler/pref part predates the diff | defer | new run telemetry-expiry-reenable-race |
| 6 | tests F1 | major | `KeyProbe.kt:127-131`, `KeyLatencyMatcherTest.kt:61` | The DOWN/repeat==0/handled gate in `KeyProbe.onKey` has no test; C5 does not test repeats | Read C5 and `KeyProbeOffTest`; no test drives repeats or `handled=false` through `onKey` into a KeyLat | fix | pending: test through `KeyProbe.onKey` (r0 answered fast, r1/r2/UP each followed by slow frames -> one KeyLat; unhandled r0 -> none) |
| 7 | tests F2 | minor | `TelemetryProbe.kt:75` | Enabling while in the foreground: nothing tests the memory timer starts from `attach` | Read L9/L12; holds | fix | pending: test in `TelemetryActivityCallbacksTest` |
| 8 | tests F3 | minor | `KeyProbe.kt:127`, `MemoryProbe.kt:99` | No test checks that key and mem lines carry the holder's screen | Read L10 and MemoryProbeTest; holds | fix | pending: assert `scr == "Home"` in L10 and MemoryProbeTest |
| 9 | tests F4 | minor | `MemoryProbeTest.kt:55` | `java_max_kb` and `native_kb` values never asserted | Read M1; holds | fix | pending: two asserts |
| 10 | tests F5 | minor | `KeyProbeOffTest.kt:42` | L13 installs the recorder after the off feed, so it cannot fail | Read the test; holds | fix | pending: recorder before the feed, end on an open DOWN |
| 11 | tests F6 | minor | evidence `:52`, `FrameAggregator.kt:52` | Evidence says `frames.t` is back-dated to the first frame, the code and F8 back-date to the last frame's end | Read both; the wording contradicts F8 or the clamp fires on device | fix | pending: qa re-checks on device (frames.t vs following nav t) and corrects the evidence |

## Rejected codex claims

- None.

## Next

- Fix round 1: implementer fixes #1, #2, #3 (toggle path), #6-#10; then check.fast, lens edge and codex-reviewer on `git diff 284bd7a0`, lens tests (tests changed), qa-device on AC-12 / AC-13 / AC-14 / AC-S2-OFF plus #11, then triage-2.
