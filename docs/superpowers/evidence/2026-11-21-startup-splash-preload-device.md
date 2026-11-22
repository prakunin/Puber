# Device evidence: startup-splash-preload (round 1)

Slug startup-splash-preload, head of the worktree branch (0c4aa01c), base a22c03bc. Emulator only: emulator-5554 (AVD puber_tv_36, API 36 TV, 1920x1080, software GPU), leased and released. No physical TV.

## Builds and installs

- devDebug `app-dev-debug.apk` sha256 a0da8e0a...e708, versionCode 2090000000, 1.12.3-dev. install-preserve: outcome installed, compatible_replace, destructive_action false; on-device base.apk sha256 equal.
- devBenchmarkRelease `app-dev-benchmarkRelease.apk` sha256 41c90833...a7bf (R8, debug-signed). M9 before install: signer 6b29181257cb...a83e and versionCode 2090000000 equal to the installed package, no signer change, no downgrade. install-preserve: installed, compatible_replace, destructive_action false, on-device sha256 equal (checked twice, see incident).
- `make DEVICE=emulator-5554 GRADLE=./tools/agentw itest` re-installed this worktree's devDebug at the end; on-device sha256 equals a0da8e0a...e708. Nothing was uninstalled, cleared or downgraded.
- Incident: the emulator process died mid-run (host load average above 40 at the time). It was restarted with the same flags as the baseline (`-no-window -no-audio -no-boot-anim -no-snapshot-save -read-only -gpu swiftshader_indirect`). The restart reverted the userdata overlay to the earlier state: the app was still installed and logged in, but the APK on it was the base devDebug from the baseline run. The benchmarkRelease APK was installed again with install-preserve (same outcome). Release set 3 and the manual checks ran after that.
- Telemetry was already enabled in the app's `telemetry_preferences` (written for the baseline, valid 7 days).

## Host load

Host 1-minute load average (8 cores) read before each run: devDebug 6.57-9.67 (baseline 6.75-9.44, comparable); devBenchmarkRelease set 1 9.55-11.97, set 2 11.19-15.63, set 3 12.64-15.37 (baseline 6.63-8.23, so every release set ran on a busier host than the baseline). Other agents were building and running a browser during the measurements.

## Method (same as baseline.md)

Cold start = `am force-stop`, 3 s, `am start -W`. 8 cold starts per build, the first dropped, 7 reported. The script runs in one `adb shell`: waits for the `startup` line in the new session file (polled every 0.1 s), then `dumpsys gfxinfo reset`, then 6 `input keyevent` (22, 21, 22, 21, 22, 21) at 500 ms spacing, 2 s more, `dumpsys gfxinfo`. `key_lat` is the telemetry event (written only above 50 ms; an unlogged key counts as 50 ms; p95 over 42 keys per set). The `startup` line is now written when both `first_content` and `revealed` exist, so the keys start at the later of the two. In all 21 release runs `revealed` came after `first_content` (median +924 ms), so the keys start at `revealed`. In devDebug 2 of 7 runs had `revealed` before `first_content` (the 2.5 s cap fired first); there the keys start up to 1.1 s after the reveal.

## AC-13 numbers (emulator, not a TV)

### devBenchmarkRelease, runs 2-8 of each set

| Metric | Baseline (first_content) | Set 1 | Set 2 | Set 3 |
|---|---|---|---|---|
| host load | 6.63-8.23 | 9.55-11.97 | 11.19-15.63 | 12.64-15.37 |
| median `revealed` ms (range) | n/a | **2278** (1848-3603) | **2733** (1673-3246) | **2023** (1962-3268) |
| median `first_content` ms (range) | **2202** (1777-2878) | 1281 (1054-2319) | 1593 (1147-1865) | 1374 (1262-2264) |
| target median `revealed` <= 2502 | | met | not met | met |
| `am start -W` TotalTime median | 751 | 726 | 1037 | 827 |
| `app_composed` median | 658 | 568 | 915 | 704 |
| Home ready (ms since Home start) median | 1070 | 497 | 486 | 451 |
| keys with key_lat > 50 ms, of 42 | 18 | 24 | 29 | 31 |
| slow keys median / max ms | 116.5 / 271 | 177 / 1644 | 581 / 1264 | 213 / 865 |
| **key_lat p95 ms (target < 199)** | **199** | **560** | **1068** | **595** |
| frames in ~5 s window, median | 69 | 59 | 48 | 57 |
| janky percent, median (software GPU: gross check) | 89.87 | 96.67 | 100 | 96.72 |
| frame time p50/p90/p95/p99 ms, medians | 85/125/150/200 | 109/150/150/200 | 150/200/250/350 | 113/150/200/250 |

Pooled over the 21 runs: median `revealed` 2374, median `first_content` 1389, key_lat p95 933 ms, 51 of 126 keys at or above 199 ms.

Raw runs, set 1 (run, load, TotalTime, app_composed, first_content, revealed, key_lat of keys above 50 ms; run 1 dropped):
1 11.47 845 749 1886 2781 549,420,184,150,84 / 2 9.55 628 522 1281 1909 86,114,106 / 3 10.47 864 677 1543 2731 560,504,295,230,122,179 / 4 11.82 710 568 1150 1952 160,136,95 / 5 10.15 726 551 1275 2278 75,178,140 / 6 11.97 897 825 1549 2473 242,176,135 / 7 11.11 637 502 1054 1848 93,388,191,215 / 8 11.26 1474 1452 2319 3603 1644,1153.

Set 2: 1 13.77 1352 .. 2005 2825 / 2 11.19 915 .. 1622 2604 / 3 13.80 1191 .. 1593 2777 / 4 15.63 1065 .. 1181 2973 / 5 15.49 1164 .. 1865 3246 / 6 15.60 882 .. 1580 2330 / 7 14.50 644 .. 1147 1673 / 8 14.75 1037 .. 1694 2733.

Set 3: 1 14.91 874 .. 3286 2630 / 2 15.19 762 .. 1389 2374 / 3 15.37 886 .. 1374 1985 / 4 13.83 774 .. 1262 2023 / 5 12.85 827 .. 1359 1981 / 6 12.64 912 .. 1843 2619 / 7 13.92 735 .. 1309 1962 / 8 13.06 1101 .. 2264 3268.

### devDebug, runs 2-8 (host load 6.57-9.67, comparable to the baseline)

| Metric | Baseline (first_content) | After |
|---|---|---|
| median `revealed` ms (range) | n/a | 4633 (3823-5372) |
| median `first_content` ms (range) | 5025 (4242-5470) | 3875 (3322-5299) |
| `am start -W` TotalTime median | 1967 | 1973 |
| `app_composed` median | 1936 | 2244 |
| keys with key_lat > 50 ms, of 42 | 8 | 2 (2043..2494 ms) |
| key_lat p95 over 42 keys ms | 1824 | 50 |
| frames median / janky median percent | 54 / 91.18 | 48 / 95.74 |
| frame time p50/p90/p95/p99 ms, medians | 93/400/700/1250 | 133/300/500/1800 |

Raw devDebug runs (load, TotalTime, app_composed, first_content, revealed, Home ready, key_lat): 1 (dropped) 6.50 2464 2554 4668 4655 1351 [2043] / 2 6.57 2279 2439 4637 3823 1246 [2299] / 3 8.06 2203 2433 5299 5288 2410 [] / 4 8.33 1973 2244 3532 4633 675 [] / 5 7.70 1881 1898 3322 4836 724 [] / 6 6.63 2304 2346 5293 5372 2221 [2494] / 7 7.75 1745 1806 3875 4232 1507 [] / 8 9.67 1723 1786 3786 4443 1275 []. Run 1 is dropped; the medians use runs 2-8. One `startup` line per cold start in every one of the 24 runs.

## Per AC and manual rows

### M1 cold start, D-pad and OK during the splash (AC-1, AC-4, AC-7)
Steps: devBenchmarkRelease, force-stop, `am start`, then one `input keyevent 20 20 20 23` (DOWN x3, CENTER) while the splash covers, screenrecord 1080p.
Observed (run with Home content ready before the cap: first_content and revealed both 4272 ms): the first key reached the app at t=3159 ms, before `revealed` 4272 ms, and no navigation or `ItemSelected` action followed (session lines: only `new_root Launcher->Main` and `tab LoadingTab->Home`); no detail screen was opened by the dropped OK. After the reveal Home showed rail, hero and Continue watching rows, no spinner and no empty carousel in the captured frames; a later Back opened the side rail, so the hero held focus after the reveal. One wordmark only: no second splash after the system launch transition in the frames.
Limit: screenrecord on this emulator wrote 20 to 36 frames per 9 s with gaps of 100 to 350 ms, so the 250 ms fade could not be stepped frame by frame, and the fade length is not measured. In two other recordings the reveal came from the 2.5 s cap under host load above 16 and showed Home's loading spinner behind the splash (the AC-5 behaviour); in one of them the slow `input` processes delivered DOWN after the reveal and moved the rail to Search.
Status: dropped keys PASS (one clean observation plus unit tests); fade and frame-by-frame checks NOT CHECKED.

### M2 network off (AC-5)
`svc wifi/data disable` and `cmd connectivity airplane-mode enable` left the emulator's eth0 route working (ping to 8.8.8.8 still answered), and `ip link set eth0 down` is refused to the shell user. Network could not be cut; both settings were restored. NOT CHECKED on device; unit rows `RevealGateTest.cap_*` cover the rule.

### M3 content link (AC-6)
`am start -W -a android.intent.action.VIEW -d puber://content/items/1000` (and /items/1) on a cold start: `startup` line `first_content` 958, `revealed` 1031 (items/1: 1808 and 1906), Details `ready Content` at 535 ms; nav `new_root Launcher->Details`. Reveal 73-98 ms after the target's first content, far before the 2.5 s cap, no Home in between. PASS.

### M4 Back while covered (AC-7)
Cold start and `input keyevent 4` at about +0.1 s: the key reached the app (key_lat `back` 1324 ms at t=1035, before reveal at 1826), the app stayed in the foreground with Home, nothing froze; Back after the reveal opened the side rail. Whether Back was consumed by the root exactly "as today" has no base observation; PASS for not swallowed and no hang, together with `StartupKeyFilterTest.backAndOtherKeysPass`.

### M5 warm start (AC-10)
`always_finish_activities 1`, HOME, relaunch: the system only brought the existing task to the front (LaunchState HOT, no new activity, no new session), and the recording showed the launcher only. A recreated activity in a live process was not produced. NOT CHECKED on device (setting restored to 0). Look of the fade: owner on the TV.

### M6 frames after reveal (AC-8)
gfxinfo windows are in the tables above (about 5 s from the anchor). Release janky percent 96.7 against 89.9 at base and p95 frame time 150-250 ms against 150 ms; on software GPU and a busier host these are a gross check only and are not distinguishable from load. The overlay's removal is shown by `StartupSplashHostTest.splashLeavesCompositionAfterTheFade`.

### M7 request counts per endpoint (AC-3)
No network log was captured. NOT CHECKED on device; `AppStartupTest.warmUpAndHomeShareOneRequestPerEndpoint` covers it.

### M8 update prompt under the splash (AC-12)
No newer release was offered during the runs (no prompt appeared). NOT CHECKED on device; `UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal` passed on the emulator.

### M9 signer and versionCode
Equal before both installs (see Builds). PASS.

### M10 startup line (AC-9)
24 of 24 cold starts wrote exactly one `startup` line, with `revealed` present each time. PASS.

### Instrumented tests (AC-4, AC-8, AC-10, AC-12)
`make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=StartupSplashHostTest,HomeLaunchFocusSettledTest,UpdatePromptOverlayFocusTest`: 15 tests, 0 failures (4 StartupSplashHostTest, 3 HomeLaunchFocusSettledTest, 8 UpdatePromptOverlayFocusTest).

## Not saved

No screenshots or recordings are stored in the repository: the Home frames show the account's continue-watching and bookmark rows.

## Round 1b: AC-13 key_lat follow-up

Date 2026-11-21. Head 0c4aa01c plus the evidence commit. Emulator-5554, devBenchmarkRelease head APK (sha256 41c90833...a7bf, installed with install-preserve, outcome installed, signer and versionCode unchanged, on-device sha256 equal), host load average 4.4 at the start of the capture.

### 1. Interleaved base/head A/B: not done, blocked on the base APK

The baseline's base APK (sha256 6cabdc4b...1ffeb) was not kept: the worktree's `app/build/outputs` now holds head, and a filesystem search found no other app-dev-benchmarkRelease APK. Building base needs either a checkout of a22c03bc away from the worktree branch or a second worktree, and the rules forbid both for me (no `git worktree add`, no stash or checkout in the worktree, `./gradlew` refused). So there is no base-versus-head key_lat comparison, no per-key-index table for base, and the load-versus-regression question is still open. Needed from the owner/conductor: a base devBenchmarkRelease APK (built by the conductor from the main checkout at a22c03bc with the same signing, or an authorised way to build it here). With it I run the 4 x 4 interleaved sets with the signer and versionCode check on every install. The head-only per-key latency of the traced run is below.

### 2. One trace on head (not stored in git; scratch only)

Capture: `atrace -t 14` with sched, freq, gfx, view, input, am, wm, dalvik, res and the app section flag, text ftrace format, from before force-stop/launch through 3.5 s after the last key; the same six keys (22, 21, 22, 21, 22, 21) at 500 ms spacing, sent by the same script as the A/B sets. No Perfetto trace processor is installed on this host, so the text trace was parsed with a small script. The app is a non-debuggable release build, so the app's own sections are limited to Compose's (`Recomposer:recompose`, `Compose:recompose`, lazy prefetch) and the framework's.

Telemetry of this run: app_composed 614, first_content 1173, revealed 1510 ms since process start (Home ready 294 ms). The keys left the script 5.37, 6.01, 6.76, 7.50, 8.29 and 8.87 s after `am start`, that is 3.5 to 7 s after the reveal, not at it: the script anchors on the `startup` line showing up in the session file, and the file only shows it several seconds after the app wrote it (the writer batches). The baseline used the same anchor, so both sides carry the same lag, but the "keys at the reveal" wording of the target does not hold for either measurement. `key_lat` per key index in this run: key 1 630 ms, key 2 540, key 3 515, key 4 408, key 5 195, key 6 164 (decreasing; frame_ms for the same events 47, 46, 71, 8, 5, 77).

What the main thread does (whole trace, 14 s):
- It is not computing: during key 1's 720 ms window it is on CPU for 154 ms, key 2 161 ms, key 3 136 ms. Of the 6.5 s spent in `traversal` over the trace, 5.4 s is `postAndWait` (the main thread waiting for the RenderThread to take the frame). Frames of 50 to 140 ms (`Choreographer#doFrame`) run back to back from launch to the end.
- RenderThread is the bottleneck: 7.4 s of `Drawing 1920x1080` and 5.2 s of `eglSwapBuffers` over 97 frames, on the software GL of the emulator; 1.4 s of `shader_compile` (18 shaders), nearly all between the reveal and key 1 (1.2 s).
- Image decoding: 29 `ImageDecoder#decodeBitmap` calls, 4.9 s of CPU summed over the Coil threads, sources 3840x2160 decoded to 533x300 (13 calls, 3.2 s) and 1800x1013 (4 calls, 0.7 s), plus 0.9 s of hardware-bitmap allocation and 0.8 s of upload. 3.5 s of that decode work falls between the reveal and key 1, 0.66 s inside the key 1-2 window, almost none after key 3.
- Compose work is small: `Recomposer:recompose` 0.9 s over 97 frames (0.42 s before the reveal, 0.22 s reveal to key 1, 0.08 s in the key 1-2 window); `Compose:recompose` 0.8 s in 465 calls. No single recomposition above 117 ms.
- JIT: 1.8 s of `Compiling baseline` on the JIT pool (0.78 s reveal to key 1, 0.34 s in the key 1-2 window): the release APK on the emulator is not AOT-compiled and the baseline profile is stale (known, out of scope).
- GC: 3 collections, 0.44 s of GC slices between the reveal and key 1, 0.08 s later; no GC on the main thread beyond that. No JSON decode shows on the main thread above the 25 ms threshold; the first 25 ms-plus main-thread slices after launch are `bindApplication` 62 ms, `activityResume` 86 ms and the first frame 367 ms.
- The fade itself is not visible as a cost: no slice tied to the splash exceeds the neighbouring frames, and the fade ends (about 0.25 s after 1.5 s) while the main thread is waiting on the RenderThread.

Reading, for the owner and the next step (hypothesis, not proven without base): the slow first keys come from the emulator's software renderer and CPU contention (4 vCPUs shared by RenderThread, Coil decoders, JIT and shader compilation), not from main-thread composition. The preload moves Home's poster loading earlier relative to the reveal, so the decode burst and shader compilation now overlap the first seconds of input; at base the same burst arrived after `first_content`, where the baseline's own keys were also sent. Whether head is slower than base at equal load needs the A/B above. On a real TV (hardware GPU) the RenderThread share should shrink, which is why the TV check stays on the manual list.

Trace file: scratch only (`/tmp/claude-1000/.../scratchpad/tr.txt`, 22 MB), not in git.

## Round 1c: interleaved base/head A/B of key_lat, two anchors

Date 2026-11-21. Emulator-5554 (software GPU), both builds devBenchmarkRelease. Base APK (built by the conductor from a clean worktree at a22c03bc) sha256 d7ae5ea9...e6ac; head APK (head 0c4aa01c, sources unchanged since) sha256 41c90833...a7bf. Before every install the tool's JSON was checked: outcome installed, destructive_action false, candidate signer = installed signer = 6b2918...a83e, versionCode 2090000000 on both sides, on-device base.apk sha256 equal to the APK. 8 installs (alternating, set 1 base first, set 2 head first, and so on), then the head APK was installed again at the end.

Incident: the emulator process was killed a second time (exit 137, host load 50+ from other agents' builds). I waited for the load to fall and restarted it with the same flags; the userdata overlay reverted (app and login intact). The scripts were pushed again. One smoke start per anchor ran before the sets and is not counted; the first version of the fixed-anchor script used a broken clock and was replaced (uptime-based).

### Method

Each set: install build X, 4 cold starts (force-stop, 3 s, start), install build Y, 4 cold starts. Starts in a set alternate the anchor: sets 1 and 3 run old, fixed, old, fixed; sets 2 and 4 run fixed, old, fixed, old.
- Anchor old (a): as baseline.md and Round 1: wait for the `startup` line in the session file, then 6 `input keyevent` (22, 21, 22, 21, 22, 21) at 500 ms. The line shows up 3 to 7 s after launch.
- Anchor fixed (b): the same six keys at 2.0, 2.5, 3.0, 3.5, 4.0 and 4.5 s after the launch command, scheduled against the device clock, identical for base and head (actual send times 2010 to 4520 ms).
- `key_lat` is the telemetry event (written only above 50 ms; an unlogged key counts as 50). Events are assigned to key indexes by the nearest send time (within 350 ms); this can swap neighbours when a key is queued for long, so per-index numbers carry that uncertainty. p95 is over all keys of the group (36 keys per cell: 6 starts x 6 keys), the second largest value.
- Per-key table cells are median/max over the 6 starts.

### Result, sets 1-3 (host load 5.1-8.7, comparable to the baseline's 6.6-8.2)

| Group | key 1 | key 2 | key 3 | key 4 | key 5 | key 6 | p95 ms | keys >= 199 ms |
|---|---|---|---|---|---|---|---|---|
| base, old anchor | 50/50 | 50/75 | 50/50 | 116/156 | 50/50 | 120/158 | 156 | 0/36 |
| head, old anchor | 50/50 | 50/119 | 50/50 | 96/208 | 50/50 | 107/190 | 190 | 1/36 |
| base, fixed (2.0 to 4.5 s) | 50/50 | 50/50 | 50/50 | 622/934 | 608/1062 | 292/503 | 934 | 15/36 |
| head, fixed (2.0 to 4.5 s) | 239/459 | 50/50 | 1577/2065 | 506/1543 | 458/764 | 451/544 | 1760 | 24/36 |

Medians of the marks (head): first_content 1166 (old runs) / 1133 (fixed runs), revealed 1841 / 1794 ms; base first_content 2083 / 2127 ms.

Set 4 (load-contaminated, reported separately, not used above): head old p95 233 (2 starts, one at load 51.6), head fixed 1 valid start (load 7.0) keys 50/1692/411/451/530/550 (its third start duplicated the second start's marks and is invalid); base ran at load 22.6 to 41.0: old p95 250, fixed p95 959 (keys 4-6 medians 841/422/300 ms).

Raw per-start values (key 1..6 latencies):
1 base #1 old load 7.91: 50,75,50,87,50,117 (first_content 2132)
1 base #2 fixed load 7.76: 50,50,50,934,621,50 (first_content 2089)
1 base #3 old load 7.53: 50,50,50,99,50,92 (first_content 1864)
1 base #4 fixed load 8.69: 50,50,50,885,434,253 (first_content 2506)
1 head #1 old load 8.45: 50,50,50,89,50,54 (first_content 1389, revealed 1873)
1 head #2 fixed load 7.13: 456,50,1538,489,529,544 (first_content 1210, revealed 1825)
1 head #3 old load 7.79: 50,50,50,71,50,190 (first_content 1204, revealed 3007)
1 head #4 fixed load 8.05: 459,50,2065,795,764,50 (first_content 1328, revealed 2030)
2 head #1 fixed load 7.17: 50,50,1760,457,458,491 (first_content 972, revealed 1499)
2 head #2 old load 6.70: 50,50,50,208,50,109 (first_content 1077, revealed 1594)
2 head #3 fixed load 6.02: 50,50,1355,371,250,187 (first_content 1137, revealed 2653)
2 head #4 old load 5.45: 50,50,50,102,50,90 (first_content 1071, revealed 1708)
2 base #1 fixed load 8.21: 50,50,50,50,901,341 (first_content 2497)
2 base #2 old load 7.36: 50,50,50,108,50,158 (first_content 1628)
2 base #3 fixed load 6.01: 50,50,50,901,361,331 (first_content 2036)
2 base #4 old load 7.50: 50,50,50,125,50,117 (first_content 2079)
3 base #1 old load 6.78: 50,50,50,135,50,124 (first_content 2086)
3 base #2 fixed load 5.82: 50,50,50,50,1062,503 (first_content 2117)
3 base #3 old load 5.33: 50,50,50,156,50,124 (first_content 2140)
3 base #4 fixed load 5.12: 50,50,50,359,594,249 (first_content 2137)
3 head #1 old load 5.30: 50,50,50,102,50,105 (first_content 1263, revealed 1914)
3 head #2 fixed load 6.77: 428,50,50,1543,458,515 (first_content 1129, revealed 1763)
3 head #3 old load 7.18: 50,119,50,79,50,136 (first_content 1128, revealed 1809)
3 head #4 fixed load 6.97: 50,50,1616,522,399,410 (first_content 1044, revealed 1658)
4 head #1 fixed load 7.00: 50,1692,411,451,530,550 (first_content 1191, revealed 1805)
4 head #2 old load 6.04: 50,50,50,134,50,98 (first_content 835, revealed 1826)
4 head #3 fixed load 7.10 (INVALID: duplicate of run 2): 50,50,50,50,50,50 (first_content 835, revealed 1826)
4 head #4 old load 51.57: 50,104,50,50,50,233 (first_content 1245, revealed 1882)
4 base #1 fixed load 40.99: 50,50,50,722,418,143 (first_content 2289)
4 base #2 old load 33.01: 250,180,50,116,50,108 (first_content 2065)
4 base #3 fixed load 26.82: 50,50,50,959,425,456 (first_content 2361)
4 base #4 old load 22.61: 50,50,50,137,50,76 (first_content 1842)

### Reading

- Old anchor: head p95 190 ms against base 156 ms (both under the 199 ms target, difference within the noise of 36 keys; 1 of 36 keys at or above 199 ms on head against 0 of 36). The Round 1 sets (p95 560 to 1068) were measured at host load 9.5 to 15.6 and are not reproduced at load 5 to 8: with equal load the old-anchor number meets the target on both builds. Round 1 was load-inflated.
- Fixed anchor (what the owner feels): both builds have slow keys while Home settles, but at different moments. Base keeps Home's loading state during 2.0 to 3.0 s (first_content around 2.1 s), so keys there cost nothing (all 50 ms or less) and the slow period comes at 3.5 to 4.5 s (median 622, 608, 292 ms), when its posters decode. Head has already revealed Home at about 1.8 s, so keys at 2.0 to 3.0 s act on a live Home that is still decoding and compiling: keys 1 and 3 median 239 and 1577 ms (key 3 up to 2065 ms), and keys 4 to 6 stay slow (506, 458, 451 ms). p95 1760 ms against 934 ms. Head is therefore interactive earlier, but the first input after the reveal waits behind the decode burst longer than base's first input does. These figures are not a like-for-like latency of the same screen: base's early keys land on a loading screen.
- With the trace of Round 1b: the cost is the RenderThread (software GL), image decoding and shader compilation after the reveal, not main-thread composition.

### Images decoded to 533x300 (from the trace, no account data)

In the head trace all decodes of large images read files from the app's own Coil disk cache (`cache/image_cache/<hashed name>`), not from the network at decode time. Decode jobs: 13 of 3840x2160 and 2 of 2160x1215/2172x1222 sources to 533x300 (16:9 card size, most likely the wide Continue-watching and bookmark card images); 4 of 3840x2160 or 2160x1215 sources to 1800x1013 (matches the hero backdrop width); 8 posters decoded at 500x750; 1 logo at 640x169; 3 at 64x64. So the cards decode full 4K-class landscape sources down to about 5 percent of their pixels (3.2 s of CPU for the 15 card decodes in the traced run), the likeliest cause of the post-reveal burst. No URLs or file names are recorded here. A card-size request that asks the image CDN for a smaller rendition, or a size hint on the Coil request, would cut it; that is a change outside this run's scope.


## Round 2: image-aware reveal and fixes (head e6e4431c, rebased on master 8428c946, Room v4)

Date 2026-11-21. Emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU), lease held and released. Head APK (devBenchmarkRelease, built from e6e4431c, git status clean) sha256 014cb459...e65. Base APK (devBenchmarkRelease, built by the conductor from master 88fa7303, code equal to 8428c946) sha256 d6ce2eaa...855. Both: signer 6b29181257cb...a83e, versionCode 2090000000.

Pre-install check: the on-device Room database had user_version 3 and identity hash 760c6d1d... (read from a pulled copy of puber.db, WAL empty); the branch schema is version 4, so every install was an upgrade, never a downgrade. Every install used install-preserve: outcome installed, compatible_replace, destructive_action false, signer equal, on-device base.apk sha256 equal to the APK (12 installs of the A/B plus one before it, checked each time). Nothing was uninstalled, cleared or downgraded.

Incident: the emulator process died once during the A/B (adb offline, QEMU hanging-thread messages in its log, host load had been 12 to 17 from other agents). It was restarted with the baseline flags (-no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect); the userdata overlay reverted to the base build installed, app and login intact. The set in progress was continued from its install step (device state matched the plan).

### AC-4 (amended): images drawn in the first frames after the fade
Cold start, devBenchmarkRelease head, screenrecord 1280x720 (emulator records about 30 frames per 4 s): in the first recorded frame after the splash began to fade (wordmark over Home) and in every later frame, the hero backdrop with title and the Continue-watching cards show their images; no skeleton or spinner frame. Telemetry of that start: first_content 1172, revealed 2759 ms. One start recorded; the fade itself spans about 2 to 3 recorded frames at this frame rate. PASS (one start; frame-by-frame resolution limited by the emulator recorder). Frames not stored (account rows).

### AC-6 / T4: content-link start reveals after the images
am start -a VIEW -d puber://content/items/1000 (cold): Details Content ready 497 ms, first_content 977, revealed 3061 ms (about 2.1 s later, i.e. held for the screen's images up to about the cap); frames: after the fade the backdrop and the Similar cards are drawn. puber://content/items/1 (cold): Content ready 1183 (since Details start), first_content 2624, revealed 3036 ms (412 ms after first_content, before the cap). Both reveal after the content mark and not before images were requested; before the fix round the reveal came 73 and 98 ms after content. PASS.

### T1: activity recreation on a non-Home screen
Cold start on Details via the content link, then `cmd uimode night yes` (recreates the activity; no configChanges in the manifest), screenrecord of 8 s across it, then two DPAD_DOWN, then `cmd uimode night no`. All 74 recorded frames show Details; the wordmark never appears; telemetry shows nav new_root Details to Details at recreation, the two key events and key_lat 83 ms for the first. No 2.5 s cover, keys delivered. PASS.

### AC-12: update prompt reveal
No newer release is offered to this install, so no prompt opened on device. NOT CHECKED on device. Not re-run: UpdatePromptOverlayFocusTest (instrumented); the unit test of the reveal call passes in check.full.

### AC-13 and AC-15: interleaved A/B, devBenchmarkRelease
Method as Round 1c: 3 sets; per set install X, one uncounted cold start, 4 counted cold starts, install Y, the same. Odd sets base first, even sets head first. Anchors alternate per start (set 1 and 3: old, fixed, old, fixed; set 2: fixed, old, fixed, old). Old anchor: six keys (22, 21, 22, 21, 22, 21, 500 ms apart) after the startup line shows in the session file. Fixed anchor: the same six keys at 2.0, 2.5, 3.0, 3.5, 4.0, 4.5 s after am start (uptime clock). Before each start the script waited up to 90 s for host load average (1 min) <= 9, then recorded it. key_lat events are assigned to key indexes by nearest send time within 350 ms; an unlogged key counts as 50 (the app logs only above 50 ms). A head key counts as "after the reveal" when its send time minus 180 ms of launch lag is at or after the revealed mark; the other head keys are "dropped under the splash". Base: all six keys.

Host load: set 1 head starts ran at load 12.2 to 16.7 (the wait cap expired; excluded below), all others 5.2 to 8.9. 24 counted starts (12 per build). Loads per start in the table.

Per start (set, build, anchor, load, key 1..6 key_lat ms, first_content, revealed); D marks a head key dropped under the splash:
1 base old load 8.88: 50,50,50,50,50,50 (first_content 4307)
1 base fixed load 8.72: 50,50,50,50,50,50 (first_content 3257)
1 base old load 8.9: 50,50,50,198,50,91 (first_content 1928)
1 base fixed load 8.71: 50,50,50,789,298,224 (first_content 2171)
1 head old load 12.43: 827,795,609,552,304,226 (first_content 2088, revealed 2974)
1 head fixed load 16.71: D1098,D633,D50,50,50,50 (first_content 2779, revealed 3093)
1 head old load 12.7: 1012,759,949,50,646,763 (first_content 2591, revealed 3232)
1 head fixed load 12.25: D726,D634,D50,50,1129,1223 (first_content 1520, revealed 5150)
2 head fixed load 7.38: D50,D913,401,594,666,671 (first_content 846, revealed 2434)
2 head old load 5.97: 50,50,50,128,50,93 (first_content 1201, revealed 3024)
2 head fixed load 8.07: D50,D934,842,919,50,898 (first_content 1237, revealed 3149)
2 head old load 7.39: 50,112,50,87,50,90 (first_content 1117, revealed 3084)
2 base fixed load 5.23: 50,50,50,1569,780,50 (first_content 2498)
2 base old load 6.04: 50,79,50,117,50,75 (first_content 1754)
2 base fixed load 6.45: 50,50,1133,455,320,427 (first_content 2067)
2 base old load 6.78: 50,50,50,130,50,81 (first_content 1612)
3 base old load 5.19: 50,50,50,106,50,120 (first_content 1875)
3 base fixed load 6.83: 50,50,734,752,483,697 (first_content 1992)
3 base old load 8.26: 50,50,50,193,50,109 (first_content 1708)
3 base fixed load 6.59: 50,50,837,344,206,50 (first_content 1706)
3 head old load 6.07: 50,50,50,71,50,88 (first_content 973, revealed 2557)
3 head fixed load 6.33: D50,D933,383,519,597,624 (first_content 916, revealed 2407)
3 head old load 6.41: 50,50,50,104,50,83 (first_content 1015, revealed 2789)
3 head fixed load 5.77: D50,834,338,331,129,93 (first_content 788, revealed 2249)

Summary at host load <= 9 (the AC-15 condition):
| Group | starts | keys counted | p95 ms | keys >= 199 ms | first_content median | revealed median |
|---|---|---|---|---|---|---|
| base, fixed | 6 | 36 (all) | 1133 | 16 of 36 (44%) | 2171 | - |
| head, fixed | 4 | 17 (7 of 24 dropped under the splash) | 919 | 14 of 17 (82%) | 916 | 2434 |
| base, old | 6 | 36 | 193 | 0 of 36 | 1875 | - |
| head, old | 4 | 24 | 112 | 0 of 24 | 1117 | 3024 |

All head starts including the load 12 to 17 ones: fixed anchor 23 keys counted (13 of 36 dropped), p95 1129 ms, 16 of 23 >= 199 ms; old anchor 36 keys, p95 949 ms, 11 >= 199.

AC-13 (median): head revealed median 2789 ms at load <= 9 (3024 ms over all 12 head starts); base first_content median 1992 ms at load <= 9; target base first_content + 300 = 2292 ms. Not met. 5 of 12 head starts revealed at 2974 to 3232 ms, about the 2.5 s cap counted from the first frame after the process start, the rest at 2249 to 2789 ms. Head first_content median 1015 ms (base 1992 ms). The old-anchor key_lat p95 is lower than base (112 against 193 ms at load <= 9), which is the other half of the Q1 target.

AC-15: head p95 919 ms <= base 1133 ms (met), but the share of head keys at >= 199 ms is 82% (14 of 17) against base 44% (16 of 36). Not met. Pattern: the key at 2.5 s and later, sent right after the reveal at 2.2 to 3.1 s, still waits 330 to 930 ms; the first delivered key after the reveal is the slow one in every start.

Trace (one head start, atrace, fixed anchor, load 5.8, revealed 3453 ms, first key after the reveal at about 3.5 s; trace and key times scratch only): ImageDecoder#decodeBitmap slices in the whole trace 20 (2.6 s summed wall), 3 of them after the reveal, none between the reveal and the first delivered key. So the image-aware reveal drains the decode burst before the reveal as designed; the first keys after the reveal (key_lat 2109, 1137, 916, 840, 936 ms under atrace overhead) are not waiting on image decoding. The remaining cost is not attributed here (round 1b: RenderThread software GL, shader compile, JIT).


## Round 2b: re-check on head e5a579a1 and a render diagnostic

Date 2026-11-21. Head e5a579a1 (the tree at HEAD 4b5c1ec5 differs only by this evidence file; a spec file was modified by another party in the worktree, not built into anything relevant), devBenchmarkRelease APK sha256 f817d02f...b7, same signer 6b2918...a83e and versionCode 2090000000; install-preserve outcome installed, compatible_replace, destructive_action false, on-device base.apk sha256 equal. Host load average 9.1 to 14.8 during the checks (other agents), 40 for a few minutes in the middle (one trace discarded, repeated at load 9.6).

### Cold starts (9 recorded, screenrecord frames of the Home start)
revealed / first_content in ms: 3091/1669 (first start after install), 2742/2734, 3499/2518, 3235/2083, 3362/1386, 3599/1303, 3007/1863, 3050/3021, 2859/1743; fixed-anchor start 4411/1643; trace start 3067/1870.
- Hero backdrop and Continue-watching card images drawn in the first frame of the fade and after it: seen in 4 of the starts I looked at frame by frame.
- Skeleton frames after the reveal in 2 starts: one at 3050 ms (revealed 29 ms after first_content, the cap value; images not drawn, expected under AC-5), one at 2742 ms (revealed 8 ms after first_content, 107 ms after the Home Content mark, before the usual cap value of about 3.0 s; hero image missing and the cards grey in the fade frames and in the last recorded frame). That second one is a reveal with images not yet drawn that did not look like the cap: 1 in 9 starts at load 9 to 15. Not reproduced at lower load; not understood.
- Keys work: the fixed-anchor start delivered all six keys; the keys after the reveal returned key_lat 2466, 707, 767 ms under load 10.6 (the first key of the sequence delivered after the reveal is again the slowest).

### Content link into Details (/items/1000, cold)
Content ready 884 ms, first_content 975, revealed 4693 ms at load about 10. Backdrop and Similar cards drawn after the fade. The reveal came 3.7 s after Content ready, later than the 3061 ms of round 2 at e6e4431c; not analysed (no trace of this start). If the 2.5 s cap runs on the main clock it can be delayed by the same 1.4 s render stall as below.

### T1: uimode toggle on Details
`cmd uimode night yes`, two DPAD_DOWN, `night no`: all 51 recorded frames show Details; the wordmark never appears; two recreations are in telemetry (nav new_root Details to Details at 11201 and 18110 ms); both keys delivered. PASS.

### Diagnostic: what the main thread and RenderThread do between revealed and the first key (one atrace, fixed anchor, load 9.6)
Start: revealed 3067 ms, first_content 1870 ms, key_lat 436, 815, 788 ms for the three keys sent before the reveal (dropped under the splash); the three later keys at 3.54, 5.0 and 5.37 s after am start were fast (no key_lat line, at most 50 ms). Times below are seconds since the app's bindApplication.
- 3.07 to 3.15 s: the first frame of the fade; main thread Choreographer#doFrame 81 ms (Record View#draw 59 ms), hands the frame to the RenderThread at 3.15 s.
- 3.147 to 4.686 s: that one RenderThread frame (Drawing 1920x1080) lasts 1539 ms. Inside it 12 consecutive shader_compile slices of 16 to 216 ms, 1.4 s in total (software GL on swiftshader), then eglSwapBuffers 113 ms. The main thread spends 1415 ms of the window in postAndWait waiting for it and is on CPU for 168 ms of the 290 ms to the first key.
- Input events sent at 3.36 s (and later) are queued behind this frame: deliverInputEvent runs at 4.691 and 4.700 s, 1.3 s after they were sent. The frames after 4.69 s are 30 to 270 ms and shrink; the later keys are fast.
- Image decoding is not drained in this start: 8 ImageDecoder#decodeBitmap slices (848 ms summed) fall between 3.07 and 3.36 s, so the reveal here was probably the cap (3067 ms, no image gate release) with posters still arriving; in the 2.4 to 3.07 s window before it 8 more (602 ms).
- Reading: the first-key latency after the reveal is the RenderThread compiling shaders in the first frame that is drawn at the reveal, not main-thread work. Whether the 250 ms full-screen alpha layer of the fade is part of that cannot be separated with this trace: the same first frame also draws images that had just been decoded (TextureOp) and Home content that was not yet visible. Frames before the reveal were cheap (11 to 45 ms), so Home's own draws under the splash did not warm those shaders. A one-off comparison without the fade (reveal at once) on the same build would separate the two; not done (no code changes). Trace and scratch data are not stored in git.
