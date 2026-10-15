# Gate - telemetry-core - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 9ce699af   **Base:** 39903d31

## Done as asked

Read `git diff 39903d31...HEAD` (109 files, +6928/-70). The diff delivers the S1 scope:
`core/telemetry` (sealed `TelemetryEvent`, allow-list `TelemetryEncoder`, `TelemetrySink` with a bounded
4096 queue and a single `puber-telemetry` writer thread, per-type caps with `meta dropped`, 2 MiB rotation,
20 MiB budget, 5 s drain plus drain on pause/export, seven-day auto-off by wall clock and by recorded
uptime), `TelemetryRuntime` (SharedPreferences switch, Timber error tree and chained uncaught handler
planted only while on), the Settings > Developer rows (toggle with indicator, Share log, overlay row
kept `BuildConfig.DEBUG`-only, section filter removed), FileProvider `telemetry/` path next to the
unchanged `Download/updates/`, and the events `session`, `startup`, `nav`, `ready`, `act`, `err`, `meta`.
`PuberVM.onAction` is now final with `handleAction` overridden in the VMs; `docs/architecture/viewmodel.md`
updated. R8: `-keepnames` for VMs, `UIAction`, `Tab`, `ui.**State`; the `log(...)` strip narrowed to the
message-only overload so `log(Throwable, ...)` reaches the tree in release.

Nothing from Out of scope: no `key`, `key_lat`, `frames`, `mem`, `net`, `play`, exit-reason, gzip or
summary tool; no new dependency (`app/build.gradle.kts` and `gradle/` untouched). The sink API matches the
spec requirement (one `Telemetry` entry with volatile `isEnabled` + `record`, sealed event, encoder in one
file, caps on the event type, no `Map<String, Any>` entry).

Documented spec deltas (plan, Implementation record; ACs untouched): D13 no clamp of a future
`enabled_at` (negative elapsed counts as zero); D14 `first_content` from a `StartupContentState`
marker on any landing VM instead of a Home-only override; `et` and frames carry full binary class names
(needed for retrace, verified in qa round 4). I accept all three.

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | T01-T04 unit; T05 instrumented (21/21, qa round 3); device D2 debug/deploy/minified | qa-report R1, R3; review-ux-designer-1 (VISUAL: match) | PASS |
| AC-1a | T07-T13 unit; device D4 chooser, zip of closed files, replace on second press, disabled "nothing to share" | qa-report R1, R3, R4 | PASS (sub-check "update-install URI resolves on a device" NOT CHECKED - no update offered; T07 proves the XML keeps the `updates` entry) |
| AC-2 | T15-T17 unit; device D1 no `telemetry/` dir, no growth when off | qa-report R1 | PASS (T18 allocation test, P2, not written; T15/T16 cover the flag) |
| AC-3 | T19-T23 unit; jq key sets over 356 pulled lines | qa-report R1 | PASS |
| AC-4 | T25-T28 unit (injected clock) + uptime bound tests (369226e1) | qa-report R1, R4; unit run at HEAD | PASS |
| AC-5 | T29-T34 unit; device act folding observed | qa-report R1; unit run at HEAD | PASS |
| AC-6 | T35/T36 real writer thread; ThreadTelemetryExecutorTest; 9ce699af makes both assert a nav line again | unit run at HEAD (I re-ran it) | PASS |
| AC-7 | T38-T41 unit; grep of typed searches/titles/token/http over 29 pulled files; audit | qa-report R1; `mobile-evidence-audit.sh docs/superpowers/evidence` re-run by me: passed, 17 files | PASS |
| AC-8 | ProguardRulesTest, TelemetryNamesTest; device minified `devBenchmarkRelease` | qa-report R1 FAIL -> fix 4d786240 -> R2 PASS | PASS |
| AC-9 | T47-T53, StartupMarksTest, StartupTabContentStateTest, SectionVMTest; device startup/nav/ready/act | qa-report R1-R4 (Home landing, debug and release) | PASS for Home landing on device; non-Home and content-list landing are unit only (1ea95f0f is after the last device run) |
| AC-10 | TelemetryErrorTree/Uncaught/ErrorPipeline tests, T62 auth via real Ktor path; device `src=log`, `am crash` with chain intact, release retrace | qa-report R1-R4 | PASS (forced network failure on device NOT CHECKED: not reproducible on the emulator) |
| AC-10a | T65-T67 unit; device toggle on/off without restart, five force-stops keep `enabled_at` | qa-report R1-R4 | PASS |
| Perf | A/B cold start off: +74 ms (+3.0 %), under max(5 %, 300 ms) | qa-report R1 | PASS |

Re-checks by me: `check.full` at HEAD 9ce699af, clean tree: BUILD SUCCESSFUL (detektAll, lintDevDebug
clean). Because Gradle reported the test task up-to-date, I forced `:app:testDevDebugUnitTest --rerun`:
BUILD SUCCESSFUL, 1770 tests, 0 failures, 0 errors, 0 skipped (results timestamped after the run,
including `StartupTabContentStateTest`, `SectionVMTest`, `TelemetryExportTest.aStalledProducerEventOlderThanTheOriginIsDroppedWhenDrained`).

Post-qa commits (1ea95f0f, 9e0dad08, 9ce699af): I read them. The production change is one marker
interface on `SectionState.Content` (only consulted inside `recordReady` when telemetry is on; Home uses
`HomeSectionState`, not `SectionState`, so the Home landing path that the device verified is unaffected)
and one `t >= origin` filter in `drainQueue` (writer thread, telemetry-on only). Both are inert with the
log off. Unit evidence is acceptable for these; a device run at HEAD does not exist and is listed below.

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| Share does not drain the queue (major) | codex-1, edge-1 F2, blind-1 F6 | fixed | d962b687; `exportContainsEventsStillQueuedWithoutAPeriodicDrain`; `exportOnWriter` drains when running (read) |
| Crash folded into a logged error (major) | edge-1 F1 | fixed | 1b740792; `TelemetryWriter.accept` bypasses folder/cap for `UNCAUGHT` (read) |
| Release `et`/frames not retraceable (major) | blind-1 F1, edge-1 F5 | fixed | 1b740792; qa R4 retrace with mapping.txt |
| `first_content` Home-only (major) | blind-1 F2 | fixed | 72af6ac6, refined 88ae5858 |
| AC-8 names in release (qa defect) | qa R1 | fixed | 4d786240; qa R2 PASS |
| Eviction deletes the just-shared zip (major) | edge-2 F1, codex-2 | fixed | 12abafde, bbe17d3e; `enforceBudget` skips newest archive (read) |
| `first_content` on error/overlay states (major) | edge-2 F2 | fixed | 88ae5858; `PuberVM.recordReady` checks `StartupContentState` (read) |
| `first_content` missing for content-list startup tab (major) | edge-3 F1 | fixed | 1ea95f0f; edge-4 clean; tests at HEAD green |
| Minor rows triage-1 5-10, 12-17; triage-2 3-6; triage-3 2, 3, 5 | edge, blind, tests, codex | fixed | commits listed in triage-1/2/3, all exist on the branch (checked with `git cat-file`) |
| No `ready` for data-class-state VMs (minor) | blind-1 F3 | deferred | new run `.flow/runs/telemetry-ready-data-states` exists |
| Uptime bound resets per process (minor) | edge-3 F2 | deferred | new run `.flow/runs/telemetry-uptime-autooff` exists |
| Codex adversarial "session-generation tag" (spot-check 1) | codex-3 | rejected | Accepted: `drainQueue` now drops `t < originElapsedMs` (TelemetrySink.kt, read) and `dropEventsOlderThanOrigin` runs on start; the generation tag adds nothing for this race |
| Content-list landing with every section empty gets no `first_content` (spot-check 2) | edge-4 "considered and rejected" | rejected | Accepted: `SectionVM` replays the paginator's `Empty` before the first load, so marking it would stamp first content before data; the 60 s fallback writes `startup` without `first_content`, which is honest |

No blocker or major in any `review-*` or `triage-*` file is left without a fix commit. None silently dropped.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Off path is one volatile read per probe (`Telemetry.isEnabled`); `PuberVM` reads `elapsedRealtime` once per VM construction even when off (negligible). Encoding and IO only on the writer thread; queue bounded at 4096; name caches are per-class. Cold start off +3.0 % A/B, within threshold. Opening Settings while off starts the writer thread for `refreshStatus` (lazy; not a startup cost). |
| Reliability | Storage null/IO error turns recording off quietly (`Unavailable`), export always answers (`Throwable` -> `Failed`), uncaught handler always chains in `finally` with a bounded 1.5 s flush (device: process died under 0.25 s), uninstall leaves a foreign later handler intact. Process death: switch in SharedPreferences, verified across force-stops. Share focus return waits for RESUMED lost/regained; if a chooser never pauses the activity the restore effect simply waits (no crash, focus stays where it is). |
| Maintainability | Follows AGENTS conventions: strings in `values`/`values-en`, `ResourceProvider` in the mapper, JUnit 5 tests, `FakeResourceProvider`. `onAction` final + `handleAction` documented in `docs/architecture/viewmodel.md`. `DeviceSettingsVM` grew to about 600 lines (detekt clean). Minor style: new imports out of order in `MainActivity.kt` and `KinoPubApiClient.kt`, a double blank line in `TelemetryNames.kt`. No dead code left (the removed `BuildConfig` import was its only use). |
| Security / privacy | No secrets in code. Lines carry class names and enums only; `err` messages go through `SensitiveRequestLogPolicy` and the scrubber; `PuberScreen.key` never logged. FileProvider adds only `external-files-path telemetry/`. Evidence audit re-run by me: passed. Residual risk stated in the spec: prose titles inside a third-party exception message cannot be scrubbed. |

## Must fix

none

## Concerns for the owner

1. No device run at HEAD: the last qa round ran on bbe17d3e. The three later commits (content-list
   `first_content` marker, pre-origin drain filter, tests) are unit-tested only; I judged them inert
   with the log off and low-risk with it on.
2. Not checked on a device: the update-install FileProvider URI with a real update; a real share target
   (the emulator offers only Bluetooth); a forced network failure `err`; `first_content` for a non-Home
   or content-list startup tab.
3. Deferred runs: `telemetry-ready-data-states` (ContentList, Settings, Main, NetworkDiagnostics emit no
   `ready`, so such a landing tab gets `first_content` only from a later screen or the 60 s fallback) and
   `telemetry-uptime-autooff` (a wrong future `enabled_at` can keep recording past seven days across
   restarts).
4. The Developer section ("For developers") is now visible in prod with the Session log and Share rows;
   wording and the indicator text are the owner's to judge on a real TV.
5. In release `err.frames` are R8 names; they need `mapping.txt` + `retrace` (qa R4 shows it works).
6. Unrelated flake: `HomeVMIncrementalPublishTest.aPublishParkedAtTheHopCannotResurrectRowsAClearAlreadyDropped`
   failed once under host load in qa round 4 (run `unit-suite-flaky`); it passed in my run.
7. Account side effect during qa round 1: Psyren S1E1 played about 6 s on the emulator account, so it is
   in History.
8. Pending owner decision in state.md: squash the branch into fewer than 5 commits before ship (tree
   unchanged).
