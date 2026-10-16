# Device evidence: telemetry frames, keys and memory probes (rounds 1 and 2)

Slug `telemetry-frames-keys-memory`, branch HEAD `441612ee` (base `cd16eb47`), size M, track perf.
No raw session files, logcat, tokens or UI dumps are recorded here: line structure, counts and `jq` summaries only.

## Build and device

- Device: `emulator-5554` (AVD `puber_tv_36`, API 36 TV, software GPU, 1920x1080, already paired). The AVD was not
  running at the start; it was cold-booted without a data wipe and the pairing was intact. Lease held with
  `tools/emulator-resource-lock.sh`, released at the end.
- Build: this worktree's own `:app:assembleDevDebug` (versionName 1.10.0-dev, sha256 `1c1d6a9b...a56c` for the
  first install, `8132dbaa...d39e` for the final clean rebuild; on-device `base.apk` sha256 identical to the
  built APK each time). `:app:assembleDevBenchmarkRelease` for D12 (sha256 `a275e13a...ca41`).
- Installs: `tools/android-apk-install-preserve`, outcome `installed`, `compatible_replace`,
  `destructive_action: false`, signer unchanged (`6b291812...49a83e`), every time (4 installs). No uninstall,
  clear, downgrade or signer change.
- Session log toggled only through the D-pad (Settings > For developers > Session log). Left OFF at the end
  (`telemetry_preferences.xml` is `<map />`). Display density override used in D10 was reset (`wm density` shows
  the physical 320).
- Host load (1-minute average) is quoted next to each relative number. The emulator renders in software:
  presence, order, field sets, cadence and `scr` are asserted; jank percentages, `p*`/`max` and `key_lat`
  magnitudes are recorded, not judged.

## Static gate

`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (AGENT_GRADLE_SERIAL=1) on `441612ee`:
BUILD SUCCESSFUL, 1971 unit tests, 0 failures, 0 skipped, detekt and lint green.

## Scenario results

### D1 off baseline and runtime toggle (AC-S2-OFF): PASS
- Toggle off at the start. Telemetry directory listing hash and file count (78 files, left over from earlier runs)
  identical before and after more than 2.5 minutes of Home scrolling, held D-pad and a cold start. Nothing created
  or grown while off.
- Toggle on with the Activity resumed (no restart): a new session file appeared at once (79 files), `frames`
  lines started in the first window.
- Toggle off at runtime: file ended with `meta ev=stop`; its size and the directory hash stayed unchanged over
  about 2.5 minutes of scrolling, the D5 script and a held D-pad (39249 bytes before and after).
- Toggle on again: a new file (80 files).

### D2 JankStats on the software GPU (gate): PASS
First `frames` line within the first window after the toggle (18 frames, `scr` empty because no nav had been seen
in the session yet), then `frames` lines for Home every window while scrolling. Not stopped, no escalation.

### D3 frames per screen (AC-12): PASS
Home scrolled about 35 s, History about 40 s, with navigation in between. Over both sessions: 61 `frames` lines;
`scr` values are `Home`, `History`, and an empty `scr` only before the first nav of a session (by design: the
holder is reset on enable). The window that closed right after the Home to History nav is written after the
`nav History` line and still says `Home`. Field set of every `frames` line is exactly
`frames,janky,max,p50,p90,p99` (jq key set, plus envelope). 0 violations of `janky <= frames`,
`p50 <= p90 <= p99 <= max`, `t >= 0`. `t` of `frames` lines is non-decreasing among `frames` lines (0 inversions);
across the whole file `frames.t` is the end of the window's last frame (`FrameAggregator.closeWindow`, clamped to
the window's open time), not its first frame; a window closed by a screen change is written after the `nav` line, so
a `frames` line may sit after later-`t` lines of other types (the spec's "t non-decreasing" is read per type).
Corrected in round 2 (see "Round 2" below); round 1 wrongly said "first frame".

### D4 memory (AC-14): PASS
- Quiet screen, 125 s idle: periodic `mem` lines at t = 60.0, 120.1, 180.1 and 240.1 s (60 s cadence, no
  duplicate), fields `java_kb,java_max_kb,native_kb,pss_kb`.
- HOME then 130 s: no periodic `mem` line. One `mem` with `trim:40` and no `pss_kb` was written by the system's
  own `onTrimMemory(BACKGROUND)` at about 5 s after backgrounding (accepted decision: a trim line is written in
  background, the periodic one is not).
- Resume: next periodic line exactly 60 s after resume (t 387.6 -> 447.6 s); one timer, not two.
- `am send-trim-memory <pkg> MODERATE`: the platform refuses it while the app process is foreground
  (`Unable to set a background trim level on a foreground process`, platform limit, not a code defect). Sent
  after HOME, it is accepted and produced `mem ... trim:60` with no `pss_kb`, `scr` Home. So API 36 delivers
  MODERATE to the callback for a backgrounded app.

### D5 D-pad behaves as before: PASS (no base-APK comparison)
Script: Home, 10 x DOWN, 5 x RIGHT, CENTER (detail), BACK x2, long-press DOWN; 21 screenshots per run, run with
log ON, OFF, ON. Same focus ring positions at every step in all three runs; CENTER opened the detail page, first
BACK returned to Home content with the same item focused, second BACK moved focus to the sidebar, the long press
moved sidebar focus by the same distance (to "Watching") in all three. No swallowed or doubled key, no focus jump,
no extra BACK needed. (Item titles differ between runs because Home content is server data that changes; positions
and transitions are identical.) Not run against a `cd16eb47` APK: none was built (not required for the
ON/OFF/ON equivalence).

### D6 key lines (AC-11): PASS
`input keyevent --longpress DPAD_DOWN` (down, repeat, up): one `key` line `n=2`, `dur_ms=410`, not per repeat.
Three separate presses: three lines `n=1`. Field set exactly `dur_ms,key,n`. 607 `key` lines over both sessions.

### D7 key_lat and clock base (AC-13): PASS (with an observation)
10 x DPAD_RIGHT, 1 s apart: 7 `key_lat` lines, `lat_ms` 58..504, `frame_ms` 41..216, all positive and below 1000;
field set `frame_ms,key,lat_ms`. Over both sessions `lat_ms` min 51; the clock bases of
`KeyEvent.eventTime` and `frameStartNanos` agree (a wrong base would give negative or six-digit values).
Observation: 7 lines had `lat_ms` 1003..1198. All of them fall in the two 80-key `input keyevent` floods (t 198-223 s),
where injected keys queue behind the main thread and `lat_ms` rises smoothly through 1034, 1061, 1198 and again
1003, 1105, 1144, 1190: genuine queueing delay, not a clock error. During the single-press check the maximum was 504.
The flood did not exceed the `key_lat` cap (max 30 per wall minute against 60), so no `meta dropped` for `key_lat`
appeared; it did for `key` (below).

### D8 volume and caps: PASS
Session `da40fb44` (8.6 minutes, mixed use plus two floods): 52,352 bytes, 340 `key`, 109 `key_lat`, 38 `frames`,
7 `mem`. Bytes per line: key 91, key_lat 105, frames 119, mem 127. Per wall minute maximum: frames 6, key_lat 30,
key 103, mem 1; the 160-press floods hit the `key` cap and produced three `meta dropped` lines
(`counts.key` 23, 2, 9). About 6.1 KB per active minute on this mix, times 60 is 0.36 MB per hour; the worst case
at the caps (12 frames, 120 key, 60 key_lat, 10 mem per minute) is about 20 KB per minute, 1.2 MB per hour, far
under the 20 MiB budget. No file near 2 MiB.

### D9 no work on hot threads (AC-S2-NODIST): PASS (proxy)
Counts only, over the whole life of the app process (about 25 minutes of use including mem ticks, density change
and trim): `Skipped N frames` Choreographer lines 0, `ANR in` 0, `FATAL EXCEPTION` 0, crash buffer lines 0. PSS cost
(temporary uncommitted local timing around `DebugMemorySampler.sample`, reverted, `git status` clean afterwards):
3 samples at 19.7, 20.9 and 21.2 ms, all on thread `puber-telemetry`. About 20 ms on this emulator is above the
"few ms" mark: not a main-thread cost, but a follow-up for `low_ram` devices (see report).

### D10 recreation and lifecycle: PASS
Two Activity recreations through `wm density 300` / `wm density reset` with scrolling in between: `frames` lines
kept coming per window (t 392.4, 403.2, 410.4, 420.7, 428.7 s), one line per window, no duplicates. Two
background/foreground cycles: `frames` resumed after each (lines at t 465.3, 467.3, 484.9 s).

### D11 privacy (AC-11a): PASS
`qwertzuiop12345` typed into Search with `input text` (screenshot confirmed the text in the field; with the soft
keyboard open the IME consumes the events, so no `key` line was written for them). Then hardware keycodes
Q W E R T Z U I O P 1 2 3 4 5 NUMPAD_7 VOLUME_UP MENU on Home: 16 `key` lines `other`, one `move_home`
(the platform remaps NUMPAD_7), one `menu`; VOLUME_UP never reaches the app. Over both session files: `qwertzuiop`
0 matches, `12345` 0, `qwer` 0, `keycode` 0. Distinct `key` values seen: `back dpad_center dpad_down dpad_left
dpad_right dpad_up menu move_home other` (all allow-list wire names). `tools/mobile-evidence-audit.sh` output is
below.

### D12 minified build (AC-S2-R8): PASS
`devBenchmarkRelease` (R8) installed with install-preserve; log pref was already on, so the session started with
the process. After scroll, a held key and 70 s: `frames` (10), `key` (50), `key_lat` (12) and `mem` (1) lines;
`session.build=benchmarkRelease`; `scr` values `Home`, `Launcher`, `Main`, `UpdatePrompt` and empty, all readable,
no `a.b.c` names. The one `err` line (a channel-logo IOException with obfuscated stack frames) is unrelated to
this change (the `err` stack frames are obfuscated by design in release). Cold start in this build 569 ms.

## Perf re-measure (devDebug, emulator-5554, load below 8 throughout)

Baseline (`baseline.md` blocks C/D, load about 6): OFF 1559 ms, ON 1594 ms, within-block spread about 110 ms.

| Block | Log | Load start -> end | Median | Mean | SD | Min | Max |
|---|---|---|---|---|---|---|---|
| OFF1 | OFF | 3.3 -> 4.2 | 1539 | 1558 | 34 | 1514 | 1616 |
| ON1 | ON | 3.1 -> 5.0 | 1546 | 1556 | 33 | 1522 | 1644 |
| OFF2 | OFF | 5.5 -> 3.8 | 1564 | 1562 | 33 | 1509 | 1617 |
| ON2 | ON | 4.2 -> 3.4 | 1551 | 1556 | 29 | 1522 | 1622 |
| (extra, pre-OFF) | ON | 3.6 -> 5.8 | 1561 | 1589 | 54 | 1529 | 1726 |

OFF vs baseline OFF: 1539 and 1564 against 1559 (-1.3 % and +0.3 %), bound `max(5 %, 2 x SD)` = 78 ms: pass.
ON vs OFF: 1546 vs 1539 and 1551 vs 1564 (+7 ms, -13 ms), bound 78 ms: pass.

Frames (`dumpsys gfxinfo reset`, 6 Down, 8 Right, 8 Left, 6 Up at 0.35 s gaps, 3 runs per state, gross check):

| State | Run | Frames | Janky | p50 | p90 | p95 | p99 | Load |
|---|---|---|---|---|---|---|---|---|
| ON | 1 | 294 | 227 (77.21 %) | 65 | 101 | 150 | 200 | 4.8 |
| ON | 2 | 280 | 205 (73.21 %) | 65 | 93 | 133 | 200 | 5.0 |
| ON | 3 | 290 | 208 (71.72 %) | 61 | 97 | 133 | 150 | 5.4 |
| OFF | 1 | 287 | 214 (74.56 %) | 65 | 97 | 133 | 150 | 3.7 |
| OFF | 2 | 297 | 222 (74.75 %) | 65 | 97 | 133 | 200 | 4.3 |
| OFF | 3 | 286 | 215 (75.17 %) | 65 | 101 | 133 | 150 | 5.1 |

ON and OFF are the same within spread (p50 61-65 ms vs 65 ms, jank 73.4 % mean vs 74.8 % mean). The baseline
(p50 150-200 ms, jank 98-99 %) was taken at host load about 11, now 4-5, so the absolute numbers are not
comparable across the two captures; the ON versus OFF comparison here is at equal load. Pass for the gross check.

PSS after 75 s idle on Home (`dumpsys meminfo`, KB, 3 samples per state, load below 2): OFF 231,879 / 225,451 /
234,202 (mean 230,511); ON 228,831 / 236,023 / 228,396 (mean 231,083). ON minus OFF +0.6 MB, inside the
spread of either state (baseline single-sample delta +3.2 MB).

## mobile-evidence-audit

`tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage`: `evidence_audit_status=passed`, 18 files scanned.

## Not done

- MANUAL M1 (real TV: true jank, realistic `key_lat`, PSS cost on hardware): needs the owner's authorization for a
  physical device; not attempted.
- D5 against a base-build APK: none was available.


# Round 2: navigation attribution, toggle-off flush (affected ACs only)

Branch HEAD `5eba2142` (fix round 1 on top of `284bd7a0`). Static gate: `./tools/agentw testDevDebugUnitTest
:app:detektAll :app:lintDevDebug` green (BUILD SUCCESSFUL, 1981 unit tests in the result XMLs, 0 failures, detekt and
lint green). Counts and `jq` summaries only below; no raw session files.

## Build and device

- Device `emulator-5554` (AVD `puber_tv_36`, API 36 TV, already running and paired). Lease acquired and released.
- This worktree's own `:app:assembleDevDebug`, sha256 `3e3f83e9...5d67`; `tools/android-apk-install-preserve`
  outcome `installed`, `compatible_replace`, `destructive_action: false`, signer unchanged (`6b291812...49a83e`); the
  on-device `base.apk` sha256 equals the built APK. No uninstall, clear, downgrade or signer change.
- The log toggle was OFF at the start and is OFF at the end (`telemetry_preferences.xml` is `<map />`). Driven only
  with the D-pad (Settings > For developers > Session log). Density is the physical 320.
- Slip, no effect on the verdicts: twice a CENTER landed on "Share log" instead of the toggle (focus one row off),
  which wrote an `act ShareSessionLog` line, rotated the session file and packed a zip in the app's own external
  files directory; once a CENTER on a History item reached the "Resume from ...?" dialog of the Player, which I
  backed out of at once with BACK (no playback was started).

## R2-1 AC-12 attribution after the fix: PASS (with a note on the brief's Back step)
- Toggle on while Settings was open: the new session file started with `scr ""` on frames/key/key_lat/mem lines (by
  design: the holder is reset on enable until the first navigation, spec D3). No line carried `DeviceSettings` or
  `DeviceSettingsFlow` as a stale name after leaving Settings.
- The brief's step "press Back in Settings" does not navigate on this build: Back in Settings only moves focus
  between the sidebar and the content, and Back from the sidebar does nothing (no `nav` line in four presses). So the
  "Back pops the parent navigator" case could not be driven through Settings; it stays proved by the unit test of
  `navigatorChangedBy` and the recorded top. What I drove instead:
  - Settings -> Home through the sidebar: `nav cmd=tab from=DeviceSettingsFlow to=Home`, and every later
    frames/key/key_lat/mem line says `Home`.
  - Home -> Details -> Back (twice, once from Home, once from History): `nav cmd=push_result ... to=Details`, then
    `nav cmd=back from=Details to=Home` (resp. `History`). The `scr` of the `nav back` line and of all lines after it
    is `Home` (resp. `History`), never `Main`. 0 lines with `scr` `Main` among frames/key/key_lat/mem in the three
    sessions (only `act` lines carry `Main`, by their own source).
  - Tab switch Home -> History -> Favorites: `nav cmd=tab` with the new tab as `to`, following lines carry it.
  - While Settings was the tab, `scr` was `DeviceSettingsFlow` (the tab's content class), which is the selected tab's
    content, not the host.
- Observations: a push produces two `nav` lines about 1 ms apart (the root runner's `to=TabRoot`, then the child's
  `to=Details`); the second wins, so `scr` is right from then on. After a Back to a tab an extra `nav cmd=tab` with
  `from` and `to` the same tab follows about 130-150 ms later. Both are redundant, not wrong.

## R2-2 AC-S2-OFF toggle-off flush: PASS (open burst not driven on device)
Two off toggles, each after a few quick D-pad presses so a frames window was open:
- Off 1: last lines of the session file, in order: `frames` (83 frames, window open at the press), `key dpad_center`,
  `act ToggleSessionLog`, `meta ev=stop`. Nothing else. File md5 identical after 75 s of further D-pad use, the
  directory listing unchanged (133 files).
- Off 2 (new session): `frames` (11 frames), `key dpad_center`, `act ToggleSessionLog`, `meta ev=stop`; file md5
  identical 15 s and about 40 s later while navigating Home and Details with the log off, directory listing 135 files
  and unchanged.
- On after off: a new session file with a new session id (`8d9b247a-01`); the earlier session's files were not
  reopened (md5 unchanged).
- Not driven: an open key burst at the moment of the toggle (the toggle itself is a single CENTER press, no held
  key); that part rests on the unit tests (`TelemetryRuntimeProbesTest`, `KeyProbeOffTest`).

## R2-3 AC-13 / AC-14 quick regression: PASS
- `key_lat` lines present for DPAD and BACK presses, `lat_ms` 53..430, `frame_ms` lower than `lat_ms`, field set
  `frame_ms,key,lat_ms`.
- `mem` periodic lines in the second session at t = 60.0, 120.1, 180.1, 240.1, 300.2 s (60 s cadence), fields
  `java_kb,java_max_kb,native_kb,pss_kb`, `scr` Home/History when a screen was known.

## R2-4 D5 D-pad spot check: PASS (spot)
Log ON: Home -> down -> right -> CENTER opened Details; BACK returned to Home with the same card focused. Log OFF:
Home -> ... -> CENTER opened Details, BACK returned to Home with the same card focused. Sidebar BACK/focus handling,
CENTER on tab items and the Back-between-sidebar-and-content behaviour were the same in both states. Not a full
21-step script and no base-APK comparison.

## R2-5 triage #11: frames.t versus the following nav t
`frames.t` is the last frame's end, not the window's first frame. Window closed by a screen change, `frames.t`
minus the `nav t` that closed it: Home -> History -101 ms; Home -> Details (idle before the press) -620 ms; Details ->
Home (static page, idle) -3.2 s; History -> Favorites +185 ms (a 162 ms janky frame spanned the switch). The windows
were 10..119 frames long over several seconds, so a first-frame back-date would have been seconds earlier. With frames
being drawn up to the switch the gap is within one frame time (here up to about 190 ms on the software GPU); a larger
negative gap is the idle time before the nav, not back-dating. The round-1 wording is corrected in the D3 section
above.

## Round 3 (HEAD `4ccfb96c`): tab-host naming and the queued probe attach

Static gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:assembleDevDebug`
(AGENT_GRADLE_SERIAL=1) BUILD SUCCESSFUL, 1988 tests in the result XMLs, 0 failures, 0 skipped.
Device `emulator-5554`, lease held and released. Install through `install-preserve`: `installed`,
`compatible_replace`, not destructive, signer unchanged (`6b291812...49a83e`); the on-device `base.apk` sha256 equals the
built APK (`7ebc54eb...f3d7e9`). The log toggle is OFF at the end. Line structure only; no raw files kept.

### R3-1 AC-12 attribution: FAIL on one sub-case, PASS on the rest
- Tab switches: `nav tab from=DeviceSettingsFlow to=Home`, `Home -> History`, later lines carry `Home` / `History`.
  Cold start: `nav new_root from=Launcher to=Main`, then `nav tab from=LoadingTab to=Home` 120 ms later.
- No `TabRoot` anywhere in the nav lines of any session (grep over the last session and the nav lines of the earlier
  ones); the tab-runner line of a push reads `push_result from=Home to=Home`, then the root runner's line names the
  pushed screen.
- Push chain: Home -> CollectionDetail -> Details -> Back: `nav back from=Details to=CollectionDetail`, `scr`
  CollectionDetail afterwards. PASS. A screen pushed from the tab (CollectionDetail, Details) is named on arrival.
- FAIL: Home -> Details -> Back (and CollectionDetail -> Back onto the tab host). The nav line is
  `nav back from=Details to=Main`, `scr` is `Main` until `nav tab from=Home to=Home` arrives 130..380 ms later
  (reproduced 4 times in 3 sessions: Home -> Details twice, CollectionDetail -> Back twice). One `frames` window of 2 frames and the `act Resumed` line carry `scr=Main`.
  After the tab line everything reads `Home`. Not fixed: the tab navigator is not composed while a root screen covers
  the host, so `TabHostTelemetry.topOf(host)` is null and the name falls back to the marker class.
- True tab-local push (Home -> Show all) NOT CHECKED: on Home every row stopped at its last card with no focusable
  Show all button (tried Continue watching, Collections, Trending, Popular series, 25..40 RIGHT presses each).
  Home -> CollectionDetail did the same two-line push (tab runner, then root), so what was exercised is the chain above.
- Relaunch with the process kept: History tab, three BACK presses (rail, close and arm, exit) left the app (pid kept),
  launcher relaunch: `act UpdatePrompt`, `nav new_root Launcher -> Main`, `nav tab LoadingTab -> Home`, no line
  labelled History after the relaunch. PASS.

### R3-2 AC-S2-OFF: PASS
- Off with an idle open window: `key dpad_center`, `act ToggleSessionLog`, `meta stop`; file md5 and directory listing
  unchanged over 100 s that included 16 D-pad presses and the 60 s memory period (no `mem`, no `frames`).
- On gave a new session file (`ea447371`). Off again with 56 frames pending: `frames` (56) written once, then
  `key dpad_center`, `act`, `meta stop`; md5 and directory listing identical about 100 s later with D-pad use.
- On again gave a third session file (`d211c327`).

### R3-3 regression: PASS
- `mem` periodic at t = 60.0 s and 120.1 s (`pss_kb` present), `key_lat` 18 lines, `lat_ms` 58..324.
- D5 spot: Home -> Details -> Back restored Home with the sidebar current item and content focus as before.
- Slips: one trailer preview started and was backed out (a stray CENTER on a Details button); no account change.
  After a run of rapid BACK presses the rail once stayed visually "Home" while the History content was shown and
  would not open on LEFT; force-stop and start recovered it. Not reproduced, not analysed.
