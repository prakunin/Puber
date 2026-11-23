# Review triage - round 1

Run: release-startup-compilation   Base: e4c3b5aa   Reviewed head: a4c19ba4

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial (incomplete: Codex usage limit hit mid-pass)). No review_extra agent matched (no Compose layout or ViewModel UI-action change).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | major | `BaselineProfileGenerator.kt` rail loop (`repeat(RAIL_ENTRIES)`) | Rail journey loses its place in Search (focus moves into the text field after 100 ms), so Favourites, History and the Movies/Series content list are never profiled | Read `SearchScreenContent.kt:79-86` (focus to `textFieldFocusRequester` after `delay(100)`); grep of the checked-in `baseline-prof.txt`: search 202, favorites 0, history 0, contentlist 0 | fix | leave Search with BACK (or wait per tab), add favorites/history/contentlist to the coverage test, regenerate on the emulator, qa re-measures AC-4/AC-5/AC-8 |
| 2 | blind F1 | major | `BaselineProfileGenerator.kt` playbackProfile; `PlayerVM.exitPlayer -> saveCurrentPosition` | Playback journey writes a watch position to the paired account on each iteration; undocumented | The owner accepted this CUJ on 2026-11-23 (state.md Decisions); scenario holds, the gap is that docs and generator say nothing | fix | document the account side effect in `docs/release.md` next to the command and in the generator KDoc; behaviour unchanged per the owner's decision |
| 3 | codex | minor | `tools/generate-baseline-profile.sh` `ensure_paired` before preflight | Unpaired + unsupported device: script installs and launches before the emulator/API/one-device refusals in the make target | Read the script: `ensure_paired` runs first, refusals only inside `make baseline-profile` | fix | run the device preflight (emulator, API >= 34, reachable) before `ensure_paired` |
| 4 | blind F4, edge unproven | minor | `tools/generate-baseline-profile.sh` `is_paired` | No `adb connect`/`get-state` on the default server, so an unreachable device reads as "not paired" | Read the script; `is_paired` swallows adb errors | fix | `adb connect` a host:port serial and check `get-state` with a clear exit before `is_paired` (same preflight as #3) |
| 5 | edge F2 | minor | script `run_generation`; `Makefile` baseline-profile recipe | Interrupt leaves the logcat process and the 5038 adb server running | Read both; no trap | fix | `trap` cleanup on EXIT/INT/TERM |
| 6 | blind F3 | minor | `AuthEventLog.kt` presence check | A cleared token (sealed empty string) is logged as present=true | Read `CryptoPreferenceRepository` clear path (`saveString(name, null)` -> encrypt("")) | fix | log non-empty stored value (no value printed) |
| 7 | blind F2 | minor | `StartupMarks.kt:103-107` bound vs ProfileInstaller delay | Fast starts can lose `prof` to the 5 s bound; ready lines now wait ~5-6 s | Read code; box showed `prof` on 30/30 starts, so the race did not show there, but the timing argument holds | fix | when awaiting profile status, extend the bound (e.g. reveal + 7 s) and state the delayed line in the KDoc |
| 8 | tests F1 | minor | `TelemetryRuntimeTest.kt:327-344` | `profileStatusReachesMarksOnlyThroughTheTelemetryExecutor` passes with a direct write too | Read test and `StartupMarks.markProfileStatus` | fix | assert via `marks.onTimeout()` before running the executor, as the reviewer describes |
| 9 | tests F2 | minor | `test-design.md:43` | `offStartNeverAsksProfileStatus` listed as P0 red but was a guard | Read the Implementation record | fix | change the row to P0 guard |
| 10 | tests F3 | minor | test-design row of `coldStartStartupLineCarriesProfileStatusFromSource` | Overclaims guarding `install` wiring; only M6 guards it | Read `TelemetryRuntime.install` and the test | fix | correct the Proves text to name M6 |

## Rejected codex claims

- Separate authorization needed for a physical TV - AGENTS.md gives standing authorization for install-preserve of the dev APK on `com.kino.puber.stage` on the box; the real defect is ordering (#3).
- Adversarial remark on telemetry scope - observation, not a defect.

## Next

- Fix round 1: implementer fixes #1-#10 (code first, then one regeneration on host.docker.internal:5555 with the owner available in case pairing was lost). Then check.fast, qa-device re-measures AC-3/AC-4/AC-5/AC-8 on the box (profile changed), then incremental review round 2: lens edge, lens tests (tests changed), codex on `git diff a4c19ba4`. Unproven lines: 6 (blind 2, edge 3, tests 2, minus the one promoted into #4 = 6).
