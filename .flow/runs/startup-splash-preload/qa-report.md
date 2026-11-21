Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass on the second full run (2172 tests, 0 failures, 0 skipped; detekt and lint green). The first full run failed once: ItemDetailsRepositoryTest.readingAWarmedItemRecordsItWithTheStampTheValueCarries hit the 1 min runTest timeout while the host load average was above 28 (class untouched by this diff, see the unit-suite-flaky run); the immediate re-run was green.
DEVICE: emulator-5554 (AVD puber_tv_36, API 36 TV). Needed: the diff touches TV UI, focus, key dispatch, startup, DI, DB-free networking warm-up and the activity. The emulator died once mid-run (host load above 40) and was restarted with the baseline's flags; the restart reverted the userdata overlay to an earlier state (app and login intact, APK back at base), and the release APK was installed again with install-preserve. Lease acquired and released.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | LauncherVMTest.start_opensMainScreenWithoutASplashAction and startTwice_*; device: one wordmark, no second splash in the cold-start frames |
| AC-2 | PASS | LauncherVMTest (IO dispatcher, domain gate, three routes), AppStartupTest start_appliesTheSavedDomainOffTheMainThread; routing observed on device (Main, Details via puber:// link) |
| AC-3 | PASS (unit) / NOT CHECKED (device) | AppStartupTest warmUpStartsHotAndWatching..., warmUpAndHomeShareOneRequestPerEndpoint, ApiDomainInteractorTest.autoResolve_concurrentCallersShareOneProbe; no request count captured on device (M7) |
| AC-4 | PASS (unit, instrumented, partial device) | HomeVMTest, HomeLaunchFocusSettledTest (3 on emulator, green), StartupSplashHostTest.contentUnderTheSplashTakesLaunchFocus; device: one clean cold start showed Home with rail, hero and rows and no spinner after the reveal, a later Back opened the rail (hero held focus). Frame-by-frame fade not obtained (see AC-10) |
| AC-5 | PASS (unit) / NOT CHECKED (device) | RevealGateTest.remainingCapMs_countsFromBegin, cap_uncoversWithoutReady; the emulator network could not be cut (eth0 stays up, no root). Under host load the cap fired on device in 4 of the recorded starts and revealed Home's loading state, which is the AC-5 behaviour |
| AC-6 | PASS | PuberVMRevealTest (4 rows), PuberVMTelemetryTest; device: puber://content/items/1000 and /1 cold starts revealed 73 and 98 ms after the Details first content, before the cap |
| AC-7 | PASS | StartupKeyFilterTest, RevealGateTest.markReady_uncoversImmediately, DispatchWithProbeTest; device: DOWN x3 + OK before the reveal navigated nowhere, Back reached the app and did not hang |
| AC-8 | PASS | StartupSplashHostTest.splashLeavesCompositionAfterTheFade and splashStaysWhileCovered on emulator (green); gfxinfo windows reported but load-confounded |
| AC-9 | PASS | StartupMarksTest, TelemetryEncoderTest; device: 24 of 24 cold starts wrote exactly one startup line carrying revealed |
| AC-10 | NOT CHECKED | StartupSplashHostTest.splashIsDrawnInTheFirstFrame and RevealGateTest.begin_coversAgainAfterAPreviousReveal pass. Warm-start recreation could not be produced on the emulator (HOT resume only), and screenrecord on this emulator gave 20 to 36 frames per 9 s with gaps of 100 to 350 ms, so the 250 ms fade, its length and the no-black-frame claim were not stepped frame by frame. Owner judges the look on the TV |
| AC-11 | PASS | AppStartupTest.aWarmUpLandingAfterAStoreWipeIsNotServedToHome, HomeVMIncrementalPublishTest, CachedFeedTest guards |
| AC-12 | PASS (instrumented) / NOT CHECKED (device) | UpdatePromptOverlayFocusTest 8 tests green on emulator incl. updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal; no newer release offered, no prompt on device |
| AC-13 | FAIL (key_lat), median partly met | devBenchmarkRelease median revealed 2278 / 2733 / 2023 ms in three sets (target <= 2502: met in two, not in set 2); key_lat p95 from revealed 560 / 1068 / 595 ms (target < 199; base 199), pooled 933 ms, 51 of 126 keys at 199 ms or more. All three release sets ran on a busier host (load 9.5-15.6) than the baseline (6.6-8.2), so the size of the gap is inflated, but the first one to three keys after the reveal were slow in every run (typical 150-1600 ms), which the baseline did not show to that degree. devDebug (load 6.6-9.7, comparable): median revealed 4633 (base first_content 5025), first_content 3875, key_lat p95 50 (base 1824), no threshold |
| AC-14 | PASS | check.full green on the second run, see CHECK |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-splash-preload/docs/superpowers/evidence/2026-11-21-startup-splash-preload-device.md (audit passed; no screenshots or recordings stored, the Home frames show account rows)
DEFECTS:
1. AC-13 key_lat target missed on devBenchmarkRelease (emulator). Reproduce: install the worktree's app-dev-benchmarkRelease.apk, force-stop, am start -W, wait for the startup line, send input keyevent 22 21 22 21 22 21 at 500 ms spacing, read key_lat events: first keys 549, 560, 242, 388, 674, 1049 ms in several runs (p95 of 42 keys 560 ms in set 1, 595 in set 3, 1068 in set 2; target below 199). It cannot be separated from host load on this evidence: no base build was re-measured in the same minutes. Suggested next step for the owner: re-measure on a quiet host with the base and the head builds interleaved, or accept the emulator number and judge on the TV.
UNAVAILABLE: none for the device (emulator available). Physical TV not authorized.
MANUAL:
- On the TV: cold start looks right (wordmark fades into Home, no spinner, empty carousel or black frame; fade length about 250 ms), AC-10.
- On the TV: warm start (leave the app with HOME, return after the activity was destroyed) fades Home in without a blank frame, AC-10.
- On the TV: D-pad pressed during the splash does nothing, and the first presses right after the reveal feel responsive (key_lat above); Back during the splash behaves as before.
- Network unreachable at launch: splash lifts at about 2.5 s and the state responds to D-pad (AC-5, M2).
- With a newer release available: prompt opens under the splash, focus lands on Update after the reveal, Back dismisses it (AC-12, M8).
- Optional: request count per endpoint per cold start from a debug network log (AC-3, M7).


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


## Round 2

Head e6e4431c (APKs built from it; conductor notes a fix commit is coming, not rebuilt here). Evidence: /home/user1/orca/workspaces/Puber/startup-splash-preload/docs/superpowers/evidence/2026-11-21-startup-splash-preload-device.md

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (2214 tests, 0 failures, 0 skipped, 0 errors; detekt and lint green).
DEVICE: emulator-5554 (AVD puber_tv_36). Room on-device v3, branch v4: installs are upgrades. Emulator died once mid-A/B and was restarted with the baseline flags (userdata reverted to the base build, app and login intact).
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 amended | PASS (1 start) | cold start frames: hero backdrop and Continue-watching card images drawn in the first frame of the fade and after; no skeleton; first_content 1172, revealed 2759 ms; recorder frame rate limits the resolution |
| AC-6 / T4 | PASS | puber://content/items/1000: revealed 3061 ms, Content ready 497, images drawn after fade; /items/1: revealed 3036, 412 ms after first_content, before the cap |
| T1 | PASS | uimode night yes/no on Details: no wordmark in 74 frames, keys delivered, key_lat 83 ms |
| AC-12 | NOT CHECKED (device) | no newer release offered; the unit test passes, instrumented test not re-run |
| AC-13 | FAIL (median) | head revealed median 2789 ms (all starts 3024) vs target base first_content median 1992 + 300 = 2292; head first_content median 1015 vs base 1992; old-anchor key_lat p95 112 vs 193 (met) |
| AC-15 | FAIL | load <= 9: head p95 919 <= base 1133 (met) but share of head keys >= 199 ms 14 of 17 (82%) vs base 16 of 36 (44%); 7 of 24 head keys dropped under the splash |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-splash-preload/docs/superpowers/evidence/2026-11-21-startup-splash-preload-device.md
DEFECTS:
1. AC-13 median: revealed median 2789 ms (3024 over all head starts) misses 2292. 5 of 12 head starts revealed at 2974 to 3232 ms, about the cap. Reproduce: install the head devBenchmarkRelease APK, 4 cold starts, read revealed in the startup line. Likely cause: the image-aware hold runs to the cap on this emulator (software GL); not proven which request holds it.
2. AC-15 share criterion: first delivered key after the reveal is slow in every start (330 to 930 ms; fixed anchor). A trace shows no image decode between the reveal and the first key (decode drained before the reveal), so the residual cost is not decode; see Round 1b for the RenderThread, shader and JIT reading. Next candidate per the spec: bitmapFactoryMaxParallelism(2), or card-image-size-tier; neither is evidenced by this trace.
UNAVAILABLE: AC-12 on device (no newer release offered).
MANUAL:
- On the TV: judge the cold start, keys right after the reveal, and the update prompt with a newer release available.
- Short re-check after the conductor's fix commit (domain walk, StartupReveal frame hook): one cold start and one content-link start on the new APK.


## Round 2b

Head e5a579a1 (APK sha256 f817d02f...b7, devBenchmarkRelease, same signer and versionCode, install-preserve installed, destructive false). Host load 9 to 15 (one 40 spell, trace repeated at 9.6). Evidence: /home/user1/orca/workspaces/Puber/startup-splash-preload/docs/superpowers/evidence/2026-11-21-startup-splash-preload-device.md

CHECK: not re-run (no source change since Round 2; check.full green there at e6e4431c, the fix commits e5a579a1 not re-gated by me).
DEVICE: emulator-5554, leased and released.
ACS:
| Item | Status | Evidence |
|---|---|---|
| Cold start | PASS with one finding | 9 starts: images drawn at the fade in 4 viewed starts; keys work. Finding: one start (revealed 2742, 8 ms after first_content, before the cap) showed skeletons after the fade; one cap start (3050) too, as designed |
| Content link /items/1000 | PASS (observed) | backdrop and cards drawn after the fade; revealed 4693 ms vs Content ready 884 ms (later than 3061 at e6e4431c, unanalysed) |
| T1 | PASS | 51 frames, no wordmark, keys delivered, two recreations |
| AC-13 / AC-15 | not re-measured | |
DIAGNOSTIC (atrace): between the reveal (3.07 s) and the first delivered key the main thread is idle (waits 1415 ms in postAndWait, 168 ms on CPU). The RenderThread draws the first post-reveal frame for 1539 ms, 1.4 s of it 12 shader_compile slices (software GL). Keys sent at 3.36 s are delivered at 4.69 s. Frames after that are 30 to 270 ms. The fade's alpha layer cannot be separated from the first draw of the just-decoded images in this trace; a reveal-at-once comparison would separate them. In this start 8 image decodes still ran in the 290 ms after the reveal (probable cap).
DEFECTS:
1. Reveal with skeletons before the cap: 1 start of 9 (revealed 8 ms after first_content at 2742 ms, hero image and card images not drawn in the fade frames or the last frame). Reproduce: repeated cold starts of devBenchmarkRelease under load 9 to 15, watch the first frames after the fade. Not reproduced at lower load; cause unknown (suspect the ready mark landing before the screen's image requests exist, which T4 was meant to cover).
2. Content link reveal at 4693 ms (3.7 s after Content ready), possibly the cap delayed by the render stall; unanalysed.
MANUAL: judge the fade and the first keys on the TV (hardware GPU compiles shaders far faster).
