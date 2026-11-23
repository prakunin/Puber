# Device evidence: release-startup-compilation (verify round 1)

Date: 2026-11-23. Device: TV box 192.168.1.110:5555 (KM6, Android 10, API 29), package `com.kino.puber.stage` only. Worktree head 0d2fad28, base e4c3b5aa. Same day as the M4 baseline (`.flow/runs/release-startup-compilation/baseline.md`).

## Build and install
- New build: `devBenchmarkRelease` of 0d2fad28, apk sha256 `f4d348702fbd9409635656fe3f716cbb6f57cf7679d021e3938d2f773806331e`; on-device `base.apk` sha256 equal after every install and after the starts.
- Base build for M7: `git archive e4c3b5aa`, `devBenchmarkRelease`, apk sha256 `8be209cccc79764c7e1ae8e1652951114c23c2fb0141874ffb2a60bd244b016b` (not byte-identical to the M4 base APK `614e269a...`; packaged `baseline.prof` 14778 bytes, the old profile; the new one is 21309 bytes).
- Every install: `tools/android-apk-install-preserve`, outcome `installed`, `compatible_replace`, `destructive_action` false, same signer; no uninstall, no data clear, no downgrade. Login intact (Home reached after the update).
- Method as in baseline.md: `am force-stop`, 2.5 s, `am start`, DPAD_DOWN at launch +2.44 s, 14 s wait, one uncounted warm-up, 15 counted starts. First-key latency = `key_lat.lat_ms` of the first key; no `key_lat` line counts as 50 ms. Telemetry session log was ON.

## AC-4 / M5 (gate on S1)
| State | recipe | dexopt line | first-key median / p95 / min / max (ms) | revealed median | `prof` |
|---|---|---|---|---|---|
| New S1 | install-preserve; broadcast INSTALL_PROFILE result=1; force-stop; dump-profiles (embedded only); `compile -m speed-profile -f` Success; first launch after | `[status=speed-profile] [reason=unknown]` before and after the starts | 434 / 845 / 50 / 845 | 2664 | 1 on 15 of 15 |
| New S0 | same-APK install-preserve reinstall, `dump-profiles` "ProfileInfo: empty" both, no compile | `[status=speed-profile] [reason=install]` before and after | 2474 / 2981 / 50 / 2981 | 3381 | 2 on 15 of 15 |
| Base S0 (baseline.md) | | `[reason=install]` | 2578 / 3105 / 1766 / 3105 | 3269 | n/a |
| Base S1 (baseline.md) | | `[reason=unknown]` | 520 / 941 / 50 / 941 | 2669 | n/a |
| Base S2 (baseline.md) | | `[reason=unknown]` | <= 50 / 120 | 1866 | n/a |

Gate: new S1 p95 <= 199 ms. Observed 845 ms. **FAIL.** The gain over base S1 is 520 -> 434 ms median and 941 -> 845 ms p95. Load average before starts: new S1 3.80-5.29, new S0 4.21-5.00 (base S1 3.27-4.19): slightly higher, not enough to explain a result over four times the gate. Start 1 of S1 has no `key_lat` line (counted 50); S0 starts 7 (dropped_keys 1) and 8 have none (counted 50).

### New S1 per start (ms since process start)
| # | load | cause | first_content | revealed | ready_req | ready | dropped | prof | lat_ms | wait_ms | frame_ms |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 4.02 | ready | 941 | 2555 | 1681 | 1887 | 0 | 1 | <=50 | - | - |
| 2 | 4.05 | cap | 998 | 2660 | 1924 | 2194 | 0 | 1 | 412 | 4 | 20 |
| 3 | 4.89 | cap | 1032 | 2682 | 2123 | 2505 | 0 | 1 | 587 | 3 | 2 |
| 4 | 5.12 | cap | 1072 | 2671 | 1945 | 2295 | 0 | 1 | 466 | 7 | 2 |
| 5 | 5.05 | cap | 1044 | 2706 | 2270 | 2653 | 0 | 1 | 845 | 6 | 2 |
| 6 | 5.29 | ready | 945 | 2377 | 1784 | 2039 | 0 | 1 | 69 | 4 | 19 |
| 7 | 4.78 | cap | 1005 | 2656 | 2264 | 2329 | 0 | 1 | 434 | 4 | 19 |
| 8 | 4.99 | cap | 1030 | 2653 | 1860 | 2204 | 0 | 1 | 143 | 3 | 19 |
| 9 | 4.69 | cap | 1021 | 2696 | 2340 | 2648 | 0 | 1 | 647 | 11 | 2 |
| 10 | 4.94 | cap | 980 | 2712 | 2396 | 2626 | 0 | 1 | 574 | 4 | 26 |
| 11 | 4.88 | cap | 993 | 2658 | 1965 | 2349 | 0 | 1 | 406 | 4 | 19 |
| 12 | 4.63 | cap | 1042 | 2687 | 2050 | 2171 | 0 | 1 | 367 | 4 | 25 |
| 13 | 4.17 | cap | 988 | 2664 | 1826 | 2143 | 0 | 1 | 601 | 4 | 21 |
| 14 | 4.12 | cap | 1037 | 2671 | 1976 | 2347 | 0 | 1 | 505 | 3 | 19 |
| 15 | 3.80 | cap | 943 | 2656 | 1961 | 2327 | 0 | 1 | 370 | 4 | 19 |

### New S0 per start
| # | load | cause | first_content | revealed | ready_req | ready | dropped | prof | lat_ms | wait_ms | frame_ms |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 4.21 | cap | 1194 | 3323 | 3324 | 3605 | 0 | 2 | 2596 | 259 | 637 |
| 2 | 5.00 | cap | 1217 | 3030 | 3505 | 3788 | 0 | 2 | 2058 | 371 | 527 |
| 3 | 4.78 | cap | 1194 | 3527 | 2989 | 3749 | 0 | 2 | 2981 | 476 | 656 |
| 4 | 4.80 | cap | 1202 | 3201 | 2703 | 3914 | 0 | 2 | 2784 | 159 | 960 |
| 5 | 4.88 | cap | 1218 | 3450 | 3940 | 4209 | 0 | 2 | 2526 | 97 | 636 |
| 6 | 4.60 | cap | 1274 | 3098 | 2644 | 3377 | 0 | 2 | 2078 | 17 | 568 |
| 7 | 4.49 | cap | 1252 | 3526 | 3276 | 3620 | 1 | 2 | <=50 | - | - |
| 8 | 4.40 | cap | 1227 | 3381 | 4176 | 4512 | 0 | 2 | <=50 | - | - |
| 9 | 4.52 | cap | 1288 | 3592 | 3592 | 4331 | 0 | 2 | 2758 | 218 | 767 |
| 10 | 4.87 | cap | 1270 | 2886 | 3338 | 4111 | 0 | 2 | 2672 | 297 | 901 |
| 11 | 4.66 | cap | 1185 | 3451 | 3650 | 4653 | 0 | 2 | 2649 | 298 | 705 |
| 12 | 4.72 | cap | 1265 | 3662 | 3663 | 4660 | 0 | 2 | 2474 | 218 | 1211 |
| 13 | 4.49 | cap | 1578 | 3265 | 3837 | 4157 | 0 | 2 | 2454 | 498 | 724 |
| 14 | 4.75 | cap | 1229 | 3437 | 3734 | 4494 | 0 | 2 | 2332 | 222 | 730 |
| 15 | 4.57 | cap | 1296 | 3219 | 3737 | 4024 | 0 | 2 | 2398 | 105 | 626 |

## AC-5 box part / M6
Every counted start of new S1 and new S0 (30 of 30) has exactly one `startup` line with a `prof` field. S1: `prof` 1 on 15 of 15. S0: `prof` 2 (enqueued) on 15 of 15. No start that reached reveal lost its line. PASS.

## AC-8 / M7 (update path)
| Step | Observed |
|---|---|
| 1 base build installed, launched once | `[status=speed-profile] [reason=install]`, on-device sha equal to base APK |
| 2 new build over it, install-preserve (`install -r`), no compile | outcome `installed`, `compatible_replace`; no login screen; dexopt `[reason=install]` |
| 3 cold start, 12 s, `startup` line | one line, `prof` 2 (cause cap, revealed 2936) |
| 4 Details card, OK on Play, 6 s, BACK | telemetry: `nav Home -> Details`, `PlayClicked`, `nav Details -> Player`, player events. BACK presses 1 and 2 stayed on the Player (`ResetControlsTimer`); press 3 logged `back Player -> Details`; press 4 `back Details -> Home`. No crash, no data loss. About 6 s of real playback on the account |
| 5 conductor `cmd package bg-dexopt-job` | Success |
| 6 dumpsys | `arm: [status=speed-profile] [reason=bg-dexopt]` |
| 7 cold start, 14 s, `startup` line | one line, `prof` 1 (cause ready, revealed 2109) |
PASS. Reported as embedded profile plus one launch compiled by bg-dexopt, not as S1.

Non-blocking finding: the plan's "BACK twice" does not leave the player; it needs three presses to reach Details and a fourth for Home. Looks like player controls handling, not compared against the base build, not part of this change. `screencap` returned all-white frames on Details and the player, so there is no screenshot evidence for step 4.

---

# Round 2 (after fix round 1 for AC-4)

Date: 2026-11-23, same day as the M4 baseline. Device: TV box 192.168.1.110:5555 (KM6, API 29), package `com.kino.puber.stage` only. Worktree head dbe5fe89 (fix commit 1733ce48: every method rule of `baseline-prof.txt` marked H).

## Build and install
- `devBenchmarkRelease` of dbe5fe89, apk sha256 `0bccf4176e781a6894f252cb9711bf87e550a55e633fcf53295b878369199ec4`; packaged `baseline.prof` 29117 bytes (round 1: 21309), `baseline.profm` 1590 bytes. On-device `base.apk` sha256 equal after every install and after the starts.
- Every install: `tools/android-apk-install-preserve`, outcome `installed`, `compatible_replace`, `destructive_action` false. No uninstall, data clear or downgrade. Login intact.
- Method as in round 1 and baseline.md (cold start, DPAD_DOWN at +2.44 s, 14 s wait, one uncounted warm-up, 15 counted; no `key_lat` line counts as 50 ms). Conductor ran every `compile`, ProfileInstaller broadcast, `dump-profiles` and `bg-dexopt-job`.

## AC-4 / M5
| State | recipe | dexopt line | first-key median / p95 / min / max (ms) | revealed median | `prof` |
|---|---|---|---|---|---|
| New S1 | install-preserve; INSTALL_PROFILE broadcast `result=1` (app not launched first); force-stop; dump-profiles shows the embedded profile only, reference empty; `compile -m speed-profile -f` Success; first launch after | `[status=speed-profile] [reason=unknown]` before and after the starts | 50 / 131 / 50 / 131 | 2364 | 1 on 15 of 15 |
| New S0 | same-APK install-preserve reinstall; dump-profiles "ProfileInfo: empty" for both; no compile | `[status=speed-profile] [reason=install]` before and after | 2720 / 3058 / 50 / 3058 | 3429 | 2 on 15 of 15 |
| Round 1 S1 (0d2fad28) | | | 434 / 845 | 2664 | 1 |
| Round 1 S0 | | | 2474 / 2981 | 3381 | 2 |
| Base S0 / S1 (baseline.md) | | | 2578 / 3105 and 520 / 941 | 3269 / 2669 | n/a |

Gate: new S1 p95 <= 199 ms. Observed 131 ms. **PASS.** Caveat on the figure: 14 of 15 S1 starts have no `key_lat` line (at most 50 ms, counted as 50), so the median 50 is the floor, not a measurement. The one start with a line (start 6, cause cap) is 131 ms (wait 4, frame 17). Fourteen starts had cause `ready` (round 1: 2 of 15). S0 starts 15 has no `key_lat` line (counted 50); S0 is reported, not gated, and is unchanged (p95 3058 vs base 3105).

Compile cost (E3): `compile -m speed-profile -f` wall 15.4 s, `Compiler stats base.apk - 15269` (base install-time 4320). Reference profile after the compile: hot 24307, startup 18471, post-startup 24501 (E2 expectation about 24k hot; round 1 about 9.6k).

### New S1 per start (ms since process start)
| # | load | cause | first_content | revealed | ready_req | ready | prof | lat_ms |
|---|---|---|---|---|---|---|---|---|
| 1 | 3.80 | ready | 885 | 2404 | 1768 | 1821 | 1 | <=50 |
| 2 | 3.78 | ready | 978 | 2521 | 1833 | 1869 | 1 | <=50 |
| 3 | 3.94 | ready | 922 | 2401 | 1725 | 1885 | 1 | <=50 |
| 4 | 3.96 | ready | 954 | 2162 | 1650 | 1688 | 1 | <=50 |
| 5 | 3.81 | ready | 1000 | 2329 | 1759 | 1806 | 1 | <=50 |
| 6 | 3.93 | cap | 1139 | 2660 | 1951 | 2197 | 1 | 131 |
| 7 | 3.80 | ready | 935 | 2406 | 1759 | 1951 | 1 | <=50 |
| 8 | 3.80 | ready | 932 | 2216 | 1661 | 1752 | 1 | <=50 |
| 9 | 3.63 | ready | 923 | 2272 | 1733 | 1767 | 1 | <=50 |
| 10 | 3.61 | ready | 902 | 2367 | 1830 | 1907 | 1 | <=50 |
| 11 | 3.63 | ready | 917 | 2519 | 1998 | 2109 | 1 | <=50 |
| 12 | 3.60 | ready | 989 | 2364 | 1884 | 1945 | 1 | <=50 |
| 13 | 3.63 | ready | 945 | 2136 | 1775 | 1831 | 1 | <=50 |
| 14 | 3.61 | ready | 923 | 2292 | 1594 | 1647 | 1 | <=50 |
| 15 | 3.79 | ready | 950 | 2131 | 1518 | 1680 | 1 | <=50 |

### New S0 per start (lat_ms; all cause cap, prof 2)
Loads 4.77-5.39. lat_ms: 2794, 3058, 2663, 2006, 2427, 3042, 2865, 2848, 2252, 2572, 2746, 2720, 2882, 2188, <=50. revealed: 3429, 3218, 2974, 3232, 3523, 4309, 4651, 3078, 3609, 4012, 2913, 2966, 3092, 3472, 3919.

## AC-5 box part / M6
Every counted start of new S1 and new S0 (30 of 30) has exactly one `startup` line with a `prof` field. S1 `prof` 1 on 15 of 15, S0 `prof` 2 on 15 of 15. PASS.

## AC-8 / M7 (update path)
| Step | Observed |
|---|---|
| 1 base build (`8be209cc...`) installed, launched once | `[status=speed-profile] [reason=install]`, on-device sha equal |
| 2 new build over it, install-preserve, no compile | `installed`, `compatible_replace`; dexopt `[reason=install]` |
| 3 cold start, 12 s | one `startup` line, `prof` 2 (cause cap, revealed 2876) |
| 4 Details card, OK on Play, 6 s, BACK | telemetry: `nav Home -> Details`, `PlayClicked`, `nav Details -> Player`, player `ready` Content. BACK press 1 `back Player -> Details`, press 2 `back Details -> Home`; no crash, process alive. About 6 s of real playback on the account |
| 5 conductor `cmd package bg-dexopt-job com.kino.puber.stage` | Success, wall 15.6 s |
| 6 dumpsys | `[status=speed-profile] [reason=bg-dexopt]`, Compiler stats `base.apk - 15433` (base install-time 4320, round 1 had no figure) |
| 7 cold start, 14 s | one `startup` line, `prof` 1 (cause ready, revealed 1984) |
PASS. The bg-dexopt compile of the 24k-method profile takes 15.6 s for this package; the dexopt time is longer than before the fix (4.3 s at install) and is reported as measured, with no AC bound on it.
Round 1's note on BACK needing three presses did not reproduce (one press per screen); still not compared with the base build.

# Round 3 (after fix round 1 of the review: profiles regenerated with the rail tabs, head 540c4ee4)

Date: 2026-11-23, same day as the M4 baseline. Device: TV box 192.168.1.110:5555 (KM6, API 29), package `com.kino.puber.stage` only. Worktree head at the build: 65d7e35a (after 540c4ee4: Makefile, docs and plan only, no change to the APK content).

## Build and install
- `devBenchmarkRelease`, apk sha256 `8251ff30bcc58d0e1391c0b36f913cba2dc7c897b56fb42b565a74c86f36e6dd`; packaged `baseline.prof` 29426 bytes (round 2: 29117), `baseline.profm` 1646 bytes. On-device `base.apk` sha256 equal after every install and after the starts.
- Every install: `tools/android-apk-install-preserve`, outcome `installed`, `compatible_replace`, `destructive_action` false. No uninstall, data clear or downgrade.
- Method as in rounds 1-2 (cold start, DPAD_DOWN at +2.44 s, 14 s wait, one uncounted warm-up, 15 counted; no `key_lat` line counts as 50 ms). Conductor ran every `compile`, ProfileInstaller broadcast, `dump-profiles` and `bg-dexopt-job`. Box load before the starts was higher than round 2 for S1 (3.7-5.2 vs 3.6-3.9).

## AC-3 profile content (grep of the generated files)
- `baseline-prof.txt`: 38673 method rules, 0 without H. `startup-prof.txt` holds 24172 method rules without H and 321 with H (startup rules are not expected all-H; reported as measured).
- Rail tabs present in `baseline-prof.txt`: favorites 121 lines, history 597, contentlist 383, search 194, bookmarks 38 class/method lines; earlier: details 920, player 4603, PlayerVM 131, DetailsScreen 298, TelemetryRuntime 32, RevealGate 20. The only `toptabs` hit is `adoptTopTabsMenuIfNeeded` (live method). `startup-prof.txt` has no favorites, contentlist, PlayerVM or DetailsScreen rule. `BaselineProfileContentTest` (favorites, history, contentlist) green in check.full.

## AC-4 / M5
| State | recipe | dexopt line | first-key median / p95 / min / max (ms) | revealed median | `prof` |
|---|---|---|---|---|---|
| New S1 | install-preserve; INSTALL_PROFILE `result=1` (app not launched first); force-stop; dump-profiles: embedded profile 75141 lines, reference empty; `compile -m speed-profile -f` Success, 15833 ms | `[status=speed-profile] [reason=unknown]` before and after | 50 / **215** / 50 / 215 | 2474 | 1 on 15 of 15 |
| New S0 | same-APK install-preserve; dump-profiles empty for both; no compile | `[reason=install]` before and after | 2358 / 3497 / 50 / 3497 | 3546 | 2 on 15 of 15 |
| Round 2 S1 / S0 | | | 50 / 131 and 2720 / 3058 | 2364 / 3429 | 1 / 2 |
| Base S1 / S0 (baseline.md) | | | 520 / 941 and 2578 / 3105 | 2669 / 3269 | n/a |

Gate: new S1 p95 <= 199 ms. Observed 215 ms. **FAIL** as measured; not re-run. Caveat: 12 of 15 starts have no `key_lat` line (counted 50, so the median is a floor). Starts with a line: start 13 (cause ready) 90 ms (wait 3, frame 13), start 14 (cause cap) 215 ms (wait 4, frame 2). It is one cap-cause start; 13 of 15 starts had cause `ready`. Without start 14 the maximum would be 90 ms.

Compile cost: wall 15833 ms, `Compiler stats base.apk - 15685`; reference profile hot 24895, startup 18307, post-startup 25112.

### New S1 per start (ms since process start)
| # | load | cause | first_content | revealed | ready_req | ready | prof | lat_ms |
|---|---|---|---|---|---|---|---|---|
| 1 | 4.67 | ready | 949 | 2500 | 1869 | 1912 | 1 | <=50 |
| 2 | 4.70 | ready | 921 | 2197 | 1599 | 1758 | 1 | <=50 |
| 3 | 4.94 | ready | 953 | 2474 | 1840 | 2025 | 1 | <=50 |
| 4 | 4.95 | ready | 888 | 2455 | 1776 | 1832 | 1 | <=50 |
| 5 | 5.00 | ready | 865 | 2023 | 1657 | 1689 | 1 | <=50 |
| 6 | 5.18 | ready | 964 | 2129 | 1417 | 1687 | 1 | <=50 |
| 7 | 5.17 | cap | 938 | 2644 | 1980 | 2024 | 1 | <=50 |
| 8 | 4.89 | ready | 963 | 2557 | 1776 | 1939 | 1 | <=50 |
| 9 | 4.64 | ready | 1083 | 2550 | 2044 | 2094 | 1 | <=50 |
| 10 | 4.30 | ready | 951 | 2612 | 1941 | 1974 | 1 | <=50 |
| 11 | 4.08 | ready | 940 | 2243 | 1942 | 1976 | 1 | <=50 |
| 12 | 4.05 | ready | 908 | 2326 | 1766 | 1921 | 1 | <=50 |
| 13 | 3.83 | ready | 917 | 2502 | 1983 | 2103 | 1 | 90 |
| 14 | 3.80 | cap | 910 | 2635 | 2205 | 2243 | 1 | 215 |
| 15 | 3.72 | ready | 952 | 2470 | 1829 | 1890 | 1 | <=50 |

### New S0
All 15 starts cause cap, prof 2, loads 4.1-5.1. lat_ms: 3497, 2212, 3003, 2250, 3273, 2331, <=50 (start 7), 2403, 2472, 2467, 2358, 2744, 1965, 2057, 2258. revealed median 3546.

## AC-5 box part / M6
30 of 30 counted starts have exactly one `startup` line with `prof`: S1 1 on 15 of 15, S0 2 on 15 of 15. PASS.

## AC-6 APK assets
`unzip -l` of the fresh devBenchmarkRelease: `assets/dexopt/baseline.prof` 29426 B and `baseline.profm` 1646 B. The prod release APK in the worktree (11:50) and nonMinifiedRelease (12:49) also list them but predate the regeneration, so they are not proof of the new content. PASS for the fresh build; prodRelease not rebuilt.

## AC-8 / M7 (update path)
| Step | Observed |
|---|---|
| 1 base build (`8be209cc...`) installed, launched once | `[status=speed-profile] [reason=install]` |
| 2 new build over it, install-preserve, no compile | `installed`, `compatible_replace`; `[reason=install]` |
| 3 cold start, 12 s | one `startup` line, `prof` 2 (cause cap, revealed 3131) |
| 4 Details card, OK on Play, 6 s | `PlayClicked`, `nav Details -> Player`, player `ready` Content; about 6 s of playback on the account |
| 5 BACK | Player -> Details after 3 presses, Details -> Home 1 more; process alive, no crash. Round 2: one press per screen; not compared with base |
| 6 conductor `cmd package bg-dexopt-job com.kino.puber.stage` | Success, wall 16534 ms (app was still running) |
| 7 dumpsys | `[status=speed-profile] [reason=bg-dexopt]`, `Compiler stats base.apk - 16289` (install-time 4320; round 2 15433) |
| 8 force-stop, cold start, 14 s | one `startup` line, `prof` 1 (cause ready, revealed 2136) |
PASS. dexopt time 16.3 s vs 4.3 s at install, no AC bound.

## AC-7
`docs/release.md` re-read at 65d7e35a: one command `DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh` (-> `make baseline-profile`), device preflight, account-write note, and the new paragraph on the S/P-only copy with restore on failure. The Makefile recipe matches (strip H, generate, re-mark H on success, restore `.bak` on failure). Single-command route holds. PASS (read, not run: generation needs an emulator).

# Round 4 (AC-4 extra sample)

Owner decision (state.md, 2026-11-23): 30 more S1 starts with the same method; AC-4 p95 is computed over all 45 starts (15 from round 3 plus these 30); threshold 199 ms unchanged; no start dropped or re-run.

## Build and S1 recipe
- Same APK as round 3: `devBenchmarkRelease`, sha256 `8251ff30bcc58d0e1391c0b36f913cba2dc7c897b56fb42b565a74c86f36e6dd` (file still in the worktree, not rebuilt). On-device `base.apk` sha256 equal after the install and after the starts.
- install-preserve: outcome `installed`, `compatible_replace`, `destructive_action` false, same signer. No uninstall, data clear or downgrade. App not launched after the install.
- Conductor then ran: `dump-profiles` both empty; INSTALL_PROFILE `result=1`; force-stop; `compile -m speed-profile -f` Success, wall 15801 ms, `Compiler stats base.apk - 15665`; dumpsys `[status=speed-profile] [reason=unknown]` (same after the starts).
- Method as rounds 1-3: cold start, DPAD_DOWN at +2.44 s, 14 s wait, one uncounted warm-up, 30 counted; no `key_lat` line counts as 50 ms. Box load 2.79-5.47 (round 3: 3.7-5.2).

## Result
| Set | n | median | p95 (nearest rank) | max | starts with a `key_lat` line |
|---|---|---|---|---|---|
| Round 3 | 15 | 50 | 215 (rank 15) | 215 | 2 (90, 215) |
| Round 4 | 30 | 50 | 50 (rank 29) | 292 | 1 (292) |
| **All** | **45** | **50** | **90 (rank 43 of 45)** | 292 | 3 |

Sorted top of the 45: ..., 50, 90 (rank 43), 215 (44), 292 (45). Gate: p95 <= 199 ms; measured 90 ms, so the gate holds as measured. Caveats: 42 of 45 starts have no `key_lat` line and count as 50, so the median is a floor. Two of 45 starts are over 199 ms (215, 292); both have cause `cap`, the other 43 have cause `ready`. The rank-43 value stays under the gate only while at most two starts exceed it. In the 30-start set, 29 had cause `ready`; start 7 (cause cap, revealed 2650) gave 292 ms (wait 4, frame 12). `prof` 1 and one `startup` line in 30 of 30. Revealed median of the 30: 2366.5.

### Round-4 S1 per start (ms since process start)
| # | load | cause | first_content | revealed | ready_req | ready | prof | lat_ms |
|---|---|---|---|---|---|---|---|---|
| 1 | 4.45 | ready | 863 | 2294 | 1775 | 1873 | 1 | <=50 |
| 2 | 4.43 | ready | 877 | 2488 | 1925 | 1983 | 1 | <=50 |
| 3 | 4.35 | ready | 937 | 2584 | 1905 | 1942 | 1 | <=50 |
| 4 | 4.55 | ready | 913 | 2229 | 1664 | 1717 | 1 | <=50 |
| 5 | 4.78 | ready | 898 | 2240 | 1574 | 1727 | 1 | <=50 |
| 6 | 4.93 | ready | 952 | 2334 | 1841 | 1895 | 1 | <=50 |
| 7 | 4.70 | cap | 948 | 2650 | 2154 | 2227 | 1 | 292 (wait 4, frame 12) |
| 8 | 4.81 | ready | 1054 | 2434 | 1849 | 1930 | 1 | <=50 |
| 9 | 5.26 | ready | 930 | 2378 | 1749 | 1836 | 1 | <=50 |
| 10 | 5.23 | ready | 929 | 2413 | 1701 | 1874 | 1 | <=50 |
| 11 | 5.14 | ready | 920 | 2412 | 1639 | 1814 | 1 | <=50 |
| 12 | 5.20 | ready | 877 | 2378 | 1676 | 1835 | 1 | <=50 |
| 13 | 5.36 | ready | 935 | 2262 | 1746 | 1845 | 1 | <=50 |
| 14 | 5.47 | ready | 877 | 2566 | 1819 | 1978 | 1 | <=50 |
| 15 | 5.45 | ready | 917 | 2347 | 1872 | 1912 | 1 | <=50 |
| 16 | 5.20 | ready | 949 | 2479 | 1792 | 1846 | 1 | <=50 |
| 17 | 5.30 | ready | 853 | 2326 | 1806 | 1868 | 1 | <=50 |
| 18 | 4.92 | ready | 951 | 2434 | 1798 | 1940 | 1 | <=50 |
| 19 | 4.97 | ready | 916 | 2397 | 1736 | 1898 | 1 | <=50 |
| 20 | 4.69 | ready | 920 | 2404 | 1825 | 2016 | 1 | <=50 |
| 21 | 4.74 | ready | 845 | 2453 | 1877 | 1916 | 1 | <=50 |
| 22 | 4.47 | ready | 877 | 2355 | 1726 | 1882 | 1 | <=50 |
| 23 | 4.05 | ready | 845 | 2128 | 1625 | 1683 | 1 | <=50 |
| 24 | 4.24 | ready | 825 | 2119 | 1608 | 1657 | 1 | <=50 |
| 25 | 3.75 | ready | 885 | 2195 | 1758 | 1792 | 1 | <=50 |
| 26 | 3.45 | ready | 803 | 2193 | 1689 | 1751 | 1 | <=50 |
| 27 | 3.26 | ready | 847 | 2415 | 1817 | 1848 | 1 | <=50 |
| 28 | 3.10 | ready | 799 | 2321 | 1654 | 1822 | 1 | <=50 |
| 29 | 2.79 | ready | 793 | 2112 | 1562 | 1708 | 1 | <=50 |
| 30 | 3.00 | ready | 821 | 1984 | 1569 | 1729 | 1 | <=50 |
