# Gate - telemetry-frames-keys-memory - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 4d9c4e31   **Base:** cd16eb47

## Done as asked

I read `git diff cd16eb47...HEAD` myself (production part: 19 files, plus tests and docs). It does what the spec asks:

- `frames` windows: `FrameAggregator` (101-bucket histogram, lazy close on a screen change, at 10 s, or on pause/detach; `t` clamped to the window's open time) is fed by `FrameProbe` through a `FrameTracker` seam over `JankStats.createAndTrack`. Per frame it only writes fields; it calls `Telemetry` only when a window closes.
- `key` bursts (`KeyBurstMachine`), `key_lat` (`KeyLatencyMatcher`: armed only on a handled first DOWN, answered by the first frame that starts after dispatch, expires after 1 s, emits only above 50 ms), and the `KeyName` allow-list, so a raw code never reaches an event.
- `mem` (`MemoryProbe`): a 60 s repeating sample on the telemetry executor while in the foreground, a trim line without `pss_kb`, `UI_HIDDEN` ignored, and a `DebugMemorySampler` with a reused `Debug.MemoryInfo`.
- The writer has a `ScreenScoped` branch (D4). The new types have per-minute caps. `MainActivity.dispatchKeyEvent` is a single `Telemetry.isEnabled` check followed by super when the log is off.
- Probe lifecycle: `TelemetryActivityCallbacks` replaces `PauseFlushCallbacks`, which is fully removed (no dead code). Attach and detach are posted to main. On toggle-off the probes are flushed inline before `sink.stop()`, and a queued attach checks `sink.isEnabled`.

Nothing from Out of scope was built: no `slow_frame`, no `mem` on screen change, no sub-50 ms answers, no image-cache size, no total frame duration.

Deviation from the spec text, all driven by fixes: spec D3 says `FlowComponent.kt` "is not edited". The round-1 and round-3 fixes for wrong `scr` attribution changed it (`navigatorChangedBy`, the tab-host binding in `TabFlowNavigator`, `TabRootScreen` made internal with `contentScreenClass`). They also added the core marker `TabHostScreen`, implemented by `MainScreen`, plus `LocalTabHostScreen`. These changes serve AC-12 and were reviewed in rounds 2 and 3. I accept them.

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-11 key bursts | KeyBurstMachineTest K5-K11, KeyBurstThroughSinkTest K12, DispatchWithProbeTest K1-K4, KeyProbeOffTest; device D6 | qa R1: long press gave `n=2 dur_ms=410`, 3 presses gave 3 lines `n=1`, field set `key,n,dur_ms` | PASS |
| AC-11a allow-list | KeyNameTest (all 5 tests present; I sampled them), encoder test; device D11 | qa R1: letters and digits gave `other`, 0 matches for the typed strings. Caveat: the IME swallows typed text | PASS |
| AC-12 frame windows | FrameAggregatorTest F1-F10, FrameAggregatorThroughSinkTest, FrameProbeTest, TelemetryWriterScrTest W1-W5, NavTelemetryTest, NavigatorChangedByTest; device D2/D3 and R2/R3 attribution | qa R1 D3: 61 `frames` lines on Home/History, a window closing after a nav keeps its `scr`. R3: tab switches PASS, push chain Back PASS, relaunch PASS. R3 defect: Back from Details onto the tab host is labelled `Main` for 130-380 ms (one 2-frame window plus one `act`), then settles on Home | PASS with concern (transient mislabel deferred to telemetry-tab-host-back-label). Show all on device: NOT CHECKED. Back that pops a parent on device: NOT CHECKED (unit-covered) |
| AC-13 key_lat | KeyLatencyMatcherTest C1-C12, KeyLatencyMatcherConcurrencyTest, KeyProbeGateTest (repeats/UP never arm; unhandled never arms); device D7 | qa R1: `lat_ms` 58..504, all positive and below 1000. R2 53..430, R3 58..324 | PASS |
| AC-14 mem | MemoryProbeTest M1-M10, MemoryProbeThreadTest (`samplerRunsOnlyOnTheWriterThread`), encoder rows; device D4 | qa R1: one line per 60 s, none in 130 s of background, trim line without `pss_kb`. R2/R3 periodic at 60.0/120.1 s | PASS |
| AC-S2-OFF | TelemetryRuntimeProbesTest (fresh off, toggle-off before queued attach, flush before sink stop), TelemetryActivityCallbacksTest, KeyProbeOffTest; device D1 | qa R1: directory hash unchanged while off, cold start OFF 1539/1564 vs baseline 1559 (bound 78). R2/R3: toggle-off writes the open window once, then nothing for 75-100 s | PASS |
| AC-S2-NODIST | thread-name tests (MemoryProbeThreadTest, FrameThreadingTest, KeyThreadingTest), allocation tests F10/K13; code review | qa R1: 0 skipped frames, 0 ANR in about 25 min. I read the diff: no IO, JSON or PSS on main or on the FrameMetrics thread | PASS (proxy on the emulator; real TV in M1) |
| AC-S2-R8 | device D12 on `devBenchmarkRelease` | qa R1: `frames`, `key`, `key_lat`, `mem` with readable `scr` | PASS |
| MANUAL M1 real TV | owner, physical device | none | NOT CHECKED (carried over, not upgraded) |

check.full: qa round 3 ran it on 4ccfb96c. HEAD 4d9c4e31 adds only the evidence file. I re-ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree: BUILD SUCCESSFUL, exit 0, 1988 test cases, 0 result files with failures. `git log cd16eb47..HEAD --format=%B | grep -ci co-authored-by` prints 0.

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| T1#1 [major] Back/FinishFlow popping a parent recorded the runner's top | codex-1, edge-1 F1 | fixed | 29622208, tests dc31d2e1, d7419c23 (NavigatorChangedByTest runs the real `onBackWithNavigator`) |
| T1#2 tab host named instead of the selected tab | edge-1 F2 | fixed | 5111fee7, reworked in 3c1a7208 |
| T1#3 toggle-off dropped the last window/burst | codex-1, blind-1 F1 | fixed (toggle path); writer-thread paths deferred | af57e777, ada5bcf5; run `telemetry-expiry-flush-loss` (stage idea) |
| T1#4 `scr ""` after mid-session enable | blind-1 F2 | rejected | Accepted: spec D3 says explicitly "writes `scr:""` until the next navigation instead of a wrong name" (owner-approved at gate 1). I checked spec line 177 |
| T1#5 expiry racing re-enable | blind-1 F3 | deferred | run `telemetry-expiry-reenable-race` (stage idea) |
| T1#6 [major] KeyProbe gate untested | tests-1 F1 | fixed | f498a8c1; KeyProbeGateTest has `repeatsAndUpNeverArmTheMatcher` and `unhandledFirstDownNeverArmsAndHandledOneDoes` |
| T1#7-#10 test gaps (timer from attach, scr on key/mem, mem values, L13 vacuous) | tests-1 F2-F5 | fixed | 3c2dd9e6, b11a39e3, 94dc6759 |
| T1#11 evidence `frames.t` wording | tests-1 F6 | fixed | 91096592 (qa R2 measured on device) |
| T2#1 queued attach after an inline detach | edge-2 F2, codex-2 | fixed | ada5bcf5; `toggleOffBeforeTheQueuedAttachRunsLeavesNoProbeAttached`; in the code the posted attach checks `sink.isEnabled` |
| T2#2/#3/#6 stale process-wide tab content; core importing feature `MainScreen`; untested tab-host `from` | edge-2 F1/F3, codex-2, tests-2 F3 | fixed | 3c1a7208 (`TabHostTelemetry` binding tied to the host instance, `TabHostScreen` marker) |
| T2#4/#5 FinishFlow test; test on a copy of the pop logic | tests-2 F1/F2 | fixed | d7419c23, dc31d2e1 |
| T2 codex claim: `navigatorToPop` may pick the wrong navigator | codex-2 | rejected | Accepted: I compared the code. `navigatorToPop` follows the same `canPop()`-then-parent recursion as `onBackWithNavigator` (FlowComponent.kt, around lines 314-331) |
| T3#1 Back onto the tab host labelled Main for 130-380 ms | edge-3 F1, codex-3, qa R3 defect 1 | deferred | run `telemetry-tab-host-back-label` (stage idea). The review cap and the bounded round 3 are recorded in state.md Decisions |
| T3#2 no Compose-level test of the tab-host binding | tests-3 F1 | deferred | folded into `telemetry-tab-host-back-label` |
| T3#3 rail shows Home with History content after rapid Back (not analysed) | qa R3 slip | deferred | run `rail-content-mismatch-rapid-back` (stage idea) |

No blocker or major was silently dropped. All three majors (T1#1, T1#6, and codex-1's major, which is the same as T1#1) have fixing commits and tests.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Cold start: OFF 1539/1564 and ON 1546/1551 ms against baseline 1559 ms, inside the 78 ms bound. PSS: ON is +0.6 MB, inside the spread. gfxinfo: ON equals OFF at equal load (only a gross check, because the software GPU makes every frame janky). Log volume is about 0.36 MB/h, at most about 1.2 MB/h at the caps. Hot paths: `onFrame` writes only fields and arrays (allocation test F10). A key repeat does not allocate (K13). `KeyLatencyMatcher.arm` allocates one `Pending` per handled first DOWN, on main and only while on; this departs slightly from D9's two AtomicLongs, and the cost is negligible. Off: one volatile flag read per key. The PSS sample costs about 20 ms on `puber-telemetry` on the emulator, which is above "a few ms"; the `low_ram` follow-up stays in M1 |
| Reliability | `runCatching` around the key probe (the dispatch return value is always super's) and around the memory sampler. Attach and detach are idempotent and posted to main. Toggle-off flushes inline on main, and a queued attach re-checks `sink.isEnabled`. Detach resets `Telemetry.screen`. Known gaps, all deferred to idea runs: expiry and storage-failure paths lose the last window (writer thread), the expiry/re-enable race, the transient Main label, and an unanalysed rail mismatch after rapid Back (possibly older than this branch) |
| Maintainability | Conventions kept. The version is in `libs.versions.toml`. Core no longer imports the feature `MainScreen` (marker interface). The `TelemetryEncoder` `when` was split into per-type builders, a refactor with tests. No duplicated helper; `PauseFlushCallbacks` removed. `TelemetryRuntime` takes defaulted test seams (`mainPost`, `isMainThread`). check.full is green (detekt, lint, 1988 tests) |
| Security / privacy | Key codes reduce to an allow-listed enum before an event is built (KeyNameTest, including a reflection guard). `scr` carries class names only, never screen keys. I re-ran `tools/mobile-evidence-audit.sh` on the evidence dir: passed, 18 files. The evidence file states that it records no raw session files, logcat, tokens or UI dumps. No secrets in the diff |
| TV | D5: D-pad focus is the same with the log ON and OFF (R1 full script, R2/R3 spot checks). There is no comparison against a base APK |

## Must fix

none

## Concerns for the owner

1. AC-12 attribution on Back to the tab host: after Back from a root-pushed screen (Details), lines are labelled `Main` for 130-380 ms (about one 2-frame `frames` window and one `act Resumed` line), then the label settles on the tab. The core of AC-12 holds: a scroll on Home or History gives its own `scr`, and so do tab switches, the push chain and relaunch. The mislabel is short and corrects itself, and the label is still `Main`, not a wrong content screen. I accept it as a deferral, not a failure. Follow-up: `telemetry-tab-host-back-label`, which also carries the missing Compose-level test of the binding.
2. MANUAL M1 (real TV) is NOT CHECKED. All jank numbers come from a software-GPU emulator, so true `p99`, realistic `key_lat` and the PSS cost on a `low_ram` device (about 20 ms per sample on the emulator) are unproven. NODIST is PASS by proxy only.
3. Not driven on device, covered by unit tests only: a tab-local push via Show all (no reachable button), and a Back that pops a parent navigator (Back in Settings only moves focus).
4. Deferred to idea runs: `telemetry-expiry-flush-loss` (the last window and burst are lost on the expiry and storage-failure paths), `telemetry-expiry-reenable-race`, `rail-content-mismatch-rapid-back` (unanalysed, possibly older than this branch).
5. The diff edits `FlowComponent.kt` and `MainScreen.kt`, although spec D3 said `FlowComponent.kt` would stay untouched. The edits are fix-driven and were reviewed in rounds 2 and 3.
6. 30 commits on the branch: under the owner's "fewer than 5 commits per run" preference, squash before `/task:ship`.
