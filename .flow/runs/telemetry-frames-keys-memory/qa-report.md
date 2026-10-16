# QA report: telemetry-frames-keys-memory

## Round 1

CHECK: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass (BUILD SUCCESSFUL, 1971 unit tests, 0 failures, 0 skipped; detekt and lint green) on `441612ee`.
DEVICE: emulator-5554 (AVD puber_tv_36, API 36, software GPU). It was not running; I cold-booted it without a data wipe, pairing intact. Lease held and released. 4 installs through install-preserve, all `installed` / `compatible_replace` / not destructive, signer unchanged. The log toggle is left OFF and the display density is back at 320.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-11 key bursts | PASS | Unit: KeyBurstMachineTest, KeyProbeOffTest, DispatchWithProbeTest. Device D6: long-press gave one `key` line `n=2`, `dur_ms=410`; 3 separate presses gave 3 lines `n=1`; field set `key,n,dur_ms`. |
| AC-11a allow-list | PASS | Unit: KeyNameTest (107 cases), encoder test. Device D11: 16 letter/digit keycodes gave `other`; 0 matches for `qwertzuiop`, `12345`, `qwer` in session files; only allow-list names seen. Caveat: with the soft keyboard open the IME swallows typed text, so no `key` line is written for it. |
| AC-12 frame windows | PASS | Unit: FrameAggregatorTest, FrameAggregatorThroughSinkTest, FrameProbeTest, TelemetryWriterScrTest. Device D2 gate passed: frames arrive on the software GPU. D3: 61 `frames` lines, `scr` Home/History, the window closing after a nav keeps its old `scr`, field set exact, 0 invariant violations. |
| AC-13 key_lat | PASS | Unit: KeyLatencyMatcherTest. Device D7: 10 presses 1 s apart gave `lat_ms` 58..504, all positive and below 1000; clock bases agree. See observation 1. |
| AC-14 mem | PASS | Unit: MemoryProbeTest. Device D4: periodic line every 60 s, none in 130 s of background, restart exactly 60 s after resume, trim line has no `pss_kb`. `send-trim-memory MODERATE` is refused for a foreground process (platform), accepted in background and gave `trim:60`. |
| AC-S2-OFF | PASS | Unit: TelemetryRuntimeProbesTest, TelemetryActivityCallbacksTest, KeyProbeOffTest, L-series. Device D1: directory hash and file count unchanged while off (two periods, about 2.5 min each); runtime on gave a new file with frames within the first window; runtime off ended the file, no growth; on again a new file. Cold start OFF 1539 / 1564 ms against baseline 1559 ms (bound 78 ms). |
| AC-S2-NODIST | PASS (proxy) | Unit thread-name and allocation tests (F10, K13, TH). Device D9: 0 `Skipped N frames`, 0 ANR, 0 FATAL, 0 crash-buffer lines over about 25 min of use. PSS sample runs on `puber-telemetry` at about 20 ms (observation 2). A real-TV pass stays M1. |
| AC-S2-R8 | PASS | D12: `devBenchmarkRelease` shows `frames`, `key`, `key_lat`, `mem`; `scr` Home/Launcher/Main/UpdatePrompt, no obfuscated names. |
| Perf: cold start | PASS | OFF1 1539, OFF2 1564 vs baseline 1559; ON 1546 / 1551 (+7, -13 ms vs OFF); bound `max(5 %, 2 x SD)` = 78 ms; load 3-6 throughout. |
| Perf: frames | PASS (gross) | gfxinfo ON p50 61-65 ms, jank 71.7-77.2 %; OFF p50 65 ms, jank 74.6-75.2 %. Baseline was taken at load about 11, now 4-5, so absolute numbers are not comparable; ON equals OFF at equal load. |
| Perf: PSS | PASS | After 75 s idle, 3 samples per state: OFF mean 230.5 MB, ON mean 231.1 MB (+0.6 MB, inside spread). |
| Perf: log volume | PASS | About 6.1 KB per active minute on a mixed session, 0.36 MB per hour; worst case at the caps about 1.2 MB per hour, against the 20 MiB budget. |
| D5 D-pad unchanged (R2) | PASS | Script run ON / OFF / ON with 21 screenshots each: same focus positions and transitions at every step. No base-APK comparison. |
| MANUAL M1 | NOT CHECKED | Real TV: needs the owner's authorization for a physical device. |

TESTS ADDED: none. The branch already has tests for every testable AC; I changed no production code. (One temporary, uncommitted timing patch to `MemoryProbe.kt` was used for the PSS cost; reverted with `git checkout`, tree clean, final APK rebuilt from the committed source and its sha256 matches the installed one.)
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-frames-keys-memory/docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md (committed `284bd7a0`; `mobile-evidence-audit.sh` passed, 18 files).
DEFECTS: none.

Observations (not defects; the first two are for the owner/reviewer):
1. D7 flood: 7 `key_lat` lines have `lat_ms` 1003..1198 (the two 80-key `input keyevent` floods, where injected keys queue behind the main thread; values climb smoothly). This is genuine queueing delay, not a clock-base error. The D7 rule "under 1000" holds for the specified 10 presses 1 s apart (max 504).
2. PSS cost about 20 ms per sample (19.7, 20.9, 21.2) on the `puber-telemetry` thread of the emulator. It is off the main thread, but above "a few ms": the plan's follow-up on `low_ram` devices applies. Real-device number stays in M1.
3. `frames.t` is back-dated to the window's first frame by design (D10), so in file order `t` is not monotonic across types; it is non-decreasing among `frames` lines (0 inversions). The spec's D3 wording "t non-decreasing" is satisfied only per type.
4. `am send-trim-memory ... MODERATE` fails with the platform's "Unable to set a background trim level on a foreground process"; it works once the app is backgrounded. The real system trim at backgrounding (`trim:40`) is also written.
5. First `frames` line of a session and the lines before the first nav carry an empty `scr` (holder reset on enable, by design).
6. The `err` line in the R8 session is a Timber channel-logo IOException unrelated to this change.
7. I started the AVD myself; it is still running (not shut down, other agents may use it).

UNAVAILABLE: none.
MANUAL: M1 on a real TV (owner authorization for a physical device): hardware-GPU `frames` (true jank, `p99`), realistic `key_lat`, PSS cost on a `low_ram` device. Optional: D5 once against a `cd16eb47` APK.

## Round 2

CHECK: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass (BUILD SUCCESSFUL, 1981 tests in the result XMLs, 0 failures; detekt and lint green) on `5eba2142`.
DEVICE: emulator-5554 (AVD puber_tv_36). Worktree's own dev debug APK (sha256 `3e3f83e9...5d67`) installed with install-preserve: `installed`, `compatible_replace`, not destructive, signer unchanged, on-device sha256 matches. Lease acquired and released. Log toggle OFF at the end.

ACS (affected only):
| AC | Status | Evidence |
|----|--------|----------|
| AC-12 attribution | PASS | Settings -> Home via sidebar: `nav tab from=DeviceSettingsFlow to=Home`, later lines `Home`. Home -> Details -> Back: `nav back to=Home` (and `History`), `scr` Home/History, 0 lines with `Main` among frames/key/key_lat/mem. Tab switch labels the tab. Caveat: the brief's "Back in Settings" does not navigate (Back only moves focus sidebar/content), so the Back-pops-parent case is proved by unit tests, not on device. |
| AC-S2-OFF / toggle-off | PASS | Two off toggles with an open frames window: the window is written once (83 and 11 frames), then `key dpad_center`, `act ToggleSessionLog`, `meta stop`; file md5 and directory listing unchanged afterwards (75 s, 40 s). On again gave a new file. Open key burst at the toggle not driven on device (unit tests only). |
| AC-13 key_lat | PASS | `key_lat` lines for DPAD/BACK, lat_ms 53..430. |
| AC-14 mem | PASS | Periodic `mem` at 60.0, 120.1, 180.1, 240.1, 300.2 s. |
| D5 D-pad unchanged | PASS (spot) | Home -> Details -> Back with log ON and OFF: same focus restored; sidebar Back behaviour same. Not the full script, no base APK. |
| Triage #11 frames.t | PASS | `frames.t` is the last frame's end: closing-nav gaps -101 ms, -620 ms, -3.2 s (idle before the nav), +185 ms (a 162 ms janky frame spanned the switch). Not ~10 s back-dated. Round-1 evidence wording corrected. |

TESTS ADDED: none.
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-frames-keys-memory/docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md (Round 2 section, committed; `mobile-evidence-audit.sh` passed, 18 files).
DEFECTS: none.

Observations: (1) a push writes two `nav` lines about 1 ms apart (`to=TabRoot`, then `to=Details`); final `scr` is right. (2) After a Back to a tab an extra `nav tab` with the same tab as from and to follows 130-150 ms later. Both redundant, not wrong; candidates for the reviewer. (3) Slips of mine: twice CENTER hit "Share log" (rotated the session file, wrote a zip in the app's external files dir) and once a Player resume dialog opened and was backed out of; no playback started.

UNAVAILABLE: none.
MANUAL: M1 unchanged (real TV, owner authorization).

## Round 3

CHECK: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (+ `:app:assembleDevDebug`) -> pass (BUILD SUCCESSFUL, 1988 tests in the result XMLs, 0 failures, 0 skipped) on `4ccfb96c`.
DEVICE: emulator-5554 (AVD puber_tv_36). Worktree's own dev debug APK installed with install-preserve (`installed`, `compatible_replace`, not destructive, signer unchanged, on-device sha256 equals the built one). Lease acquired and released. Log toggle OFF at the end.

ACS (affected only):
| AC | Status | Evidence |
|----|--------|----------|
| AC-12 tab switches label the tab | PASS | `nav tab Settings->Home`, `Home->History`; later lines `Home` / `History`. |
| AC-12 tab-runner lines name no TabRoot | PASS | 0 `TabRoot` in any nav line; tab-runner line of a push reads `from=Home to=Home`. |
| AC-12 Home -> Details -> Back -> scr Home | FAIL (transient) | `nav back from=Details to=Main`; `scr=Main` for 130..380 ms until `nav tab Home->Home`; 1 frames window (2 frames) and `act Resumed` labelled Main. 4 repros in 3 sessions. Settles on Home. |
| AC-12 pushed screen then root screen then Back names the pushed screen | PASS (push chain) / NOT CHECKED (Show all) | Home -> CollectionDetail -> Details -> Back: `nav back to=CollectionDetail`, scr CollectionDetail. Show all not reachable: no focusable Show all button at row ends on Home. |
| AC-12 relaunch, process kept | PASS | Back x3 left the app, launcher relaunch: UpdatePrompt, `new_root Launcher->Main`, `tab LoadingTab->Home`; nothing labelled History after it. |
| AC-S2-OFF toggle off | PASS | Open window written once (56 frames), then `key`, `act`, `meta stop`; md5 and directory listing unchanged ~100 s with D-pad use and a 60 s period; on/off/on gave new files; no mem, no frames after off. |
| AC-13 key_lat, AC-14 mem | PASS | key_lat 58..324 ms; mem at 60.0 and 120.1 s with pss_kb. |
| D5 D-pad spot | PASS (spot) | Home -> Details -> Back restores the same state. |

TESTS ADDED: none.
EVIDENCE: /home/user1/orca/workspaces/Puber/telemetry-frames-keys-memory/docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md (Round 3 section, committed; mobile-evidence-audit passed, 18 files).
DEFECTS:
1. Back from a root-pushed screen onto the tab host is named `Main`, not the tab. Repro: log on, Home tab, CENTER on a card (Details), BACK. Log shows `nav back from=Details to=Main`, `scr` Main until `nav tab Home->Home` ~130-380 ms later. Cause (read from code, not changed): `TabHostTelemetry.topOf(host)` is null while the tab navigator is not composed, so `navClassOf` falls back to the marker class. The round-3 intent was `scr Home` on this Back.
UNAVAILABLE: none.
MANUAL: M1 unchanged. Not exercised: a true tab-local push (Show all) because no focusable Show all button was reachable on Home.
Slips: one trailer preview started and was backed out; once the rail stuck after rapid BACK presses (recovered by force-stop and start; not reproduced).
