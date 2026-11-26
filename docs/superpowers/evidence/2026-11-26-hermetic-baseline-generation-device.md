# Device and static evidence: hermetic-baseline-generation (round 1)

Date: 2026-11-26. Worktree HEAD 15dea3ac, base 5cd3e37e.

## Builds

- Dev debug APK (this worktree): sha256 9a3e5f0043813ff0d2f8e153c8ef4cfdea94041a95dea96a91ff4136d90b2522, version 1.12.8-dev.
- Release APKs rebuilt in this worktree: assembleProdRelease, assembleDevRelease (each run alone; a combined run hit the 2 GiB Gradle heap during R8, environmental).

## Generation run (M1, M2): the implementer's run on the emulator, read not repeated

Emulator host.docker.internal:5555 (sdk_google_atv64_arm64, API 36). Run started 14:15:48 (after the last code commit 24ea13b4), finished 14:34; the profile commit 15dea3ac follows.

- 11 tests, 0 failures, 2 skipped (StartupBenchmarks, expected: outside the AC set).
- BaselineMockSelfTest: 5/5 passed (journeyCheck_fails_whenRequiredRouteMissing, journeyCheck_fails_whenUnknownRouteServed, journeyCheck_fails_whenEgressJournalNotEmpty, journeyCheck_passes_whenRequiredRoutesHitAndNothingElse, unknownRoute_returns404AndIsListedInVerify): M2 PASS.
- BaselineProfileGenerator: railNavigationProfile, browseAndDetailsProfile, startupProfile, playbackProfile all passed.
- PuberGen iteration lines: 37 iterations, 37 with missing=[] unknown=[] and an empty egress journal (VERIFY broadcast data=""). Playback: 15 iterations, each with GET /media/stream.m3u8, GET /media/seg*.ts (6) and GET /v1/watching/marktime. No watching/toggle seen.
- Dry run without .env and without PUBER_CLIENT_SECRET: 94 tasks, 0 contain Dev or Prod; generation tasks are collectInstrumentationNonMinifiedReleaseBaselineProfile only.

## M3 release APK inspection

| APK | assets/dexopt/baseline.prof | instrumentation/blocker/receiver/synthetic-auth/mock classes in dex | res/raw or pem files | manifest entries other than the profileinstaller library |
|---|---|---|---|---|
| prod release | present (30539 B) | 0 matches (142537 classes listed) | 0 | none |
| dev release | present (30539 B) | 0 matches | 0 | none |

Positive control: the same grep over the instrumentation nonMinifiedRelease APK finds 80 matches. R8 mapping files of both release builds have 0 lines for com.kino.puber.profile, BaselineProfileApp, BaselineNetworkBlocker.

## M4 rule-line counts (lines containing the package, base 5cd3e37e vs regenerated)

| Package | baseline base | baseline new | startup base | startup new |
|---|---|---|---|---|
| coil3/ | 1271 | 1430 | 1187 | 1396 |
| coil3/network/ | 158 | 223 | 128 | 220 |
| coil3/decode/ | 83 | 148 | 83 | 148 |
| io/ktor/ | 2344 | 2406 | 2242 | 2369 |
| androidx/media3/ | 4804 | 5317 | 5 | 5 |
| androidx/media3/exoplayer/hls/ | 376 | 387 | 1 | 1 |
| com/kino/puber/ | 9458 | 9412 | 5038 | 4739 |
| okhttp3/internal/tls/ | 30 | 29 | 30 | 29 |
| okhttp3/ | 2071 | 1683 | 1937 | 1647 |
| com/kino/puber/profile/ and baselineprofile/ | 20 | 0 | 20 | 0 |

No drop over 20 %. Largest: okhttp3/ baseline -18.7 %, startup -15.0 %. Total lines baseline 46351 -> 46224, startup 31145 -> 30328.
baseline-prof.txt: 0 method rules without H. startup-prof.txt keeps S/P flags (24172 non-H at base, 23546 now); the Makefile re-marks only baseline-prof.txt, unchanged from base.

## M6 launch on the TV box (AC-9)

Device 192.168.1.110:5555, package com.kino.puber.stage only, lease held and released. Steps: auth-save (saved), tools/android-apk-install-preserve install-preserve -> outcome installed, classification compatible_replace, destructive_action false, same signer. On-device sha256 of base.apk equals the APK sha256 above. One cold launch (am start -W: LaunchState COLD, TotalTime 9561 ms): Home screen with hero, "Ya smotryu" and "Novinki" rails, logged in (no pairing screen). HOME, then one relaunch (LaunchState HOT): same Home screen. logcat -b crash after both: empty. No Retry pressed, no repeated cold launches.
Not done: a screenshot of the master dev build for side by side comparison (the first screen with a live session is Home, which is what was reached). The dev debug APK does not embed the baseline profile (debug type), so the run shows the flavor/source-set change does not break launch, not the profile effect.
