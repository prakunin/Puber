# Baseline: release-startup-compilation

## Build and install
- Base e4c3b5aa, task :app:assembleDevBenchmarkRelease, device 192.168.1.110:5555
- Host APK sha256: 614e269a4e97fe6c88a5520f9f2d34d201def76b491da92c5181309658217f75
- install-preserve outcome: installed (compatible_replace, version 1.12.5-dev)
- On-device base.apk: see below

```
614e269a4e97fe6c88a5520f9f2d34d201def76b491da92c5181309658217f75  /data/app/com.kino.puber.stage-w1M-jiiDQnT511FFyJ7qOw==/base.apk
Dexopt state:
  [com.kino.puber.stage]
    path: /data/app/com.kino.puber.stage-w1M-jiiDQnT511FFyJ7qOw==/base.apk
      arm: [status=speed-profile] [reason=install]


Compiler stats:
  [com.kino.puber.stage]
     base.apk - 4320
```

## S0 (as installed)

Conductor note: `cmd package compile --reset com.kino.puber.stage` is refused on the box (API 29, "SecurityException: Only the system can clear all profile data", exit 255). Verified instead with `cmd package dump-profiles` right after install-preserve: profile and reference profile both "ProfileInfo: empty", dexopt `arm: [status=speed-profile] [reason=install]`. So the fresh install is S0 (no profile, no compiled code).

- Device: TV box 192.168.1.110:5555 (KM6, Android 10), leased and released, package com.kino.puber.stage only. Build: devBenchmarkRelease of base e4c3b5aa (1.12.5-dev), apk sha256 614e269a...217f75, on-device base.apk sha256 equal (checked after the starts).
- Compile state before the starts: `[status=speed-profile] [reason=install]`, profiles empty. After the starts (same dumpsys): `[status=speed-profile] [reason=install]` unchanged (no bg-dexopt ran); dump-profiles now shows a non-empty runtime profile for base.apk (JIT samples saved by the S0 launches, hot methods listed); reference section: 43357:=== reference profile === | 43358-ProfileInfo: empty | 3 | 43358 /data/misc/profman/com.kino.puber.stage-primary.prof.txt.
- Telemetry session log was ON (left as found). The base build has no `prof` (ProfileVerifier) field on the startup line; that arrives with the change.
- Method (as in startup-first-input-on-tv baseline, anchor A): cold start = `am force-stop`, 2.5 s, `am start`; `input keyevent KEYCODE_DPAD_DOWN` at launch +2.44 s (reaches the app about +3 s); 14 s wait; one uncounted warm-up start, then 15 counted starts. No screenrecord or pixel poll. first-key latency = the `key_lat` line's `lat_ms` of the first key (no line = at most 50 ms, counted as 50; none here lacked a line).
- Box load average (1 min) before each start: 3.18-4.11 (idle about 2.8-3.0 before the set; 3.62 after).

### Per start (ms since process start unless noted)

| # | load | cause | first_content | revealed | ready_req | ready | dropped_keys | key n | lat_ms | wait_ms | frame_ms | expired |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 3.18 | cap | 1174 | 2858 | 3390 | 4120 | 0 | 1 | 2712 | 268 | 940 | - |
| 2 | 3.32 | cap | 1131 | 2935 | 2936 | 3667 | 0 | 1 | 2578 | 233 | 620 | - |
| 3 | 3.67 | cap | 1103 | 3104 | 3269 | 3573 | 0 | 1 | 2027 | 50 | 181 | - |
| 4 | 4.05 | cap | 1094 | 3288 | 3060 | 3381 | 0 | 1 | 2320 | 21 | 159 | - |
| 5 | 3.75 | cap | 1137 | 3339 | 3341 | 4070 | 0 | 1 | 3025 | 263 | 618 | - |
| 6 | 3.52 | cap | 1193 | 3274 | 2704 | 3943 | 0 | 1 | 3105 | 350 | 633 | - |
| 7 | 3.88 | cap | 1148 | 3109 | 3110 | 3833 | 0 | 1 | 2680 | 259 | 588 | - |
| 8 | 4.11 | cap | 1099 | 3254 | 3407 | 3663 | 0 | 1 | 2189 | 52 | 168 | - |
| 9 | 3.92 | cap | 1121 | 3249 | 3250 | 4005 | 0 | 1 | 2320 | 403 | 889 | - |
| 10 | 3.90 | cap | 1122 | 3461 | 2530 | 3545 | 0 | 1 | 2593 | 101 | 205 | - |
| 11 | 4.03 | cap | 1128 | 3525 | 3010 | 3742 | 0 | 1 | 2506 | 116 | 621 | - |
| 12 | 3.98 | cap | 1196 | 3303 | 3305 | 4014 | 0 | 1 | 2648 | 116 | 594 | - |
| 13 | 3.95 | cap | 1096 | 3269 | 3410 | 3619 | 0 | 1 | 2321 | 56 | 250 | - |
| 14 | 3.98 | cap | 1156 | 3748 | 3166 | 3980 | 0 | 1 | 2786 | 146 | 672 | - |
| 15 | 3.63 | cap | 1186 | 2891 | 3042 | 3692 | 0 | 1 | 1766 | 255 | 493 | - |

### Summary (n = 15)

| metric | value |
|---|---|
| first-key lat_ms median | 2578 |
| first-key lat_ms p95 (nearest rank, n=15 so rank 15 = max) | 3105 |
| lat_ms min / max | 1766 / 3105 |
| revealed median | 3269 |
| first_content median | 1131 |
| cause | cap in 15 of 15 |
| dropped_keys | 0 in all 15 |

Compare: startup-first-input-on-tv baseline (anchor A, other build/state) median 3186.5, p95 3956; the first-key latency here sits lower at 2578 / 3105 (first_content 1131 vs 1479). Same device, same day as S1/S2 to follow.

## S2 (forced `cmd package compile -m speed-profile -f` on the S0-launched install)

- Compile run by the conductor: Success. Before the starts: dumpsys `arm: [status=speed-profile] [reason=unknown]`; dump-profiles: current profile "ProfileInfo: empty", reference profile populated (merged S0 JIT samples plus whatever ProfileInstaller wrote). Same base APK, on-device sha256 614e269a...217f75 before and after.
- After the starts: Dexopt state: |   [com.kino.puber.stage] |     path: /data/app/com.kino.puber.stage-w1M-jiiDQnT511FFyJ7qOw==/base.apk |       arm: [status=speed-profile] [reason=unknown] | 3.53 3.69 3.42 1/1489 12544 | === profile === | ProfileInfo: empty | === reference profile === | ProfileInfo: | 614e269a4e97fe6c88a5520f9f2d34d201def76b491da92c5181309658217f75  /data/app/com.kino.puber.stage-w1M-jiiDQnT511FFyJ7qOw==/base.apk
- Same method as S0 (anchor A, force-stop, 2.5 s, am start, DPAD_DOWN at launch +2.44 s, 14 s wait; one uncounted warm-up, 15 counted). Telemetry on. Box load average before each start 2.89-4.43.

### Per start

| # | load | cause | first_content | revealed | ready_req | ready | dropped_keys | key n | lat_ms | wait_ms | frame_ms | expired |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 3.41 | ready | 782 | 1821 | 1356 | 1406 | 0 | 1 | no line | - | - | - |
| 2 | 3.22 | ready | 807 | 1628 | 1195 | 1302 | 0 | 1 | no line | - | - | - |
| 3 | 3.36 | ready | 777 | 1565 | 1213 | 1259 | 0 | 1 | no line | - | - | - |
| 4 | 3.04 | ready | 736 | 1866 | 1391 | 1508 | 0 | 1 | no line | - | - | - |
| 5 | 3.15 | ready | 778 | 1688 | 1294 | 1338 | 0 | 1 | no line | - | - | - |
| 6 | 2.89 | ready | 796 | 2028 | 1362 | 1561 | 0 | 1 | no line | - | - | - |
| 7 | 3.06 | ready | 905 | 2486 | 1734 | 1833 | 0 | 1 | no line | - | - | - |
| 8 | 4.00 | cap | 1859 | 2640 | 2473 | 2632 | 0 | 1 | 120 | 3 | 13 | - |
| 9 | 3.65 | ready | 800 | 1904 | 1349 | 1382 | 0 | 1 | no line | - | - | - |
| 10 | 3.71 | ready | 774 | 1844 | 1319 | 1356 | 0 | 1 | no line | - | - | - |
| 11 | 3.54 | ready | 826 | 1918 | 1479 | 1518 | 0 | 1 | no line | - | - | - |
| 12 | 4.25 | ready | 763 | 1825 | 1297 | 1381 | 0 | 1 | no line | - | - | - |
| 13 | 4.43 | ready | 828 | 1747 | 1342 | 1374 | 0 | 1 | no line | - | - | - |
| 14 | 3.78 | ready | 811 | 1875 | 1351 | 1393 | 0 | 1 | no line | - | - | - |
| 15 | 4.12 | ready | 821 | 2027 | 1490 | 1560 | 0 | 1 | no line | - | - | - |

### Summary (n = 15)

| metric | S0 | S2 |
|---|---|---|
| first-key lat_ms median | 2578 | 50 |
| first-key lat_ms p95 (rank 15 = max) | 3105 | 120 |
| min / max | 1766 / 3105 | 50 / 120 |
| revealed median | 3269 | 1866 |
| first_content median | 1131 | 800 |
| cause | cap x15 | ready,cap |
| starts without key_lat line | 0 | 14 |

Note on S2 latency: 14 of 15 starts wrote no `key_lat` line, meaning the first key was handled in 50 ms or less (a line is only written above 50 ms). Median and p95 are therefore bounds: median <= 50 ms, p95 = 120 ms (the one start with a line, lat_ms 120); the 50 in the summary is the floor, not a measurement. Each start's key was confirmed by its `key` line (n = 1).

## S1 (base APK reinstalled, embedded profile installed by ProfileInstaller broadcast, then forced compile)

Protocol (conductor, with lease): force-stop; install-preserve of the base APK copy (sha256 614e269a...217f75; outcome installed, compatible_replace, destructive_action false; new path /data/app/com.kino.puber.stage-UL4LcusD06xM_bc_Zsw_tw==/); dump-profiles cur and ref empty, dexopt [reason=install]. ProfileInstaller INSTALL_PROFILE broadcast result=1, force-stop at once; cur profile populated (43358-line dump), ref empty. `cmd package compile -m speed-profile -f` Success; dexopt [status=speed-profile] [reason=unknown]; cur empty, ref populated (43358 lines; S2's reference dump was 57339 lines). App not launched before my starts.

Protocol note: an earlier S1 attempt at 06:54 failed at the install step because apkanalyzer was not on PATH. Its broadcast and compile ran on the S2 state, and the later reinstall wiped that state, so it is not part of any measurement.

- Before the starts (my reading): on-device base.apk sha256 614e269a...217f75 (equal to the host APK), `[status=speed-profile] [reason=unknown]`, cur "ProfileInfo: empty", reference profile populated. After the starts: same dexopt line, cur empty, ref populated, sha256 unchanged.
- Same method as S0 and S2 (anchor A; one uncounted warm-up, 15 counted). Telemetry on. Load average before each start 3.27-4.19; 3.54 before the set and 3.41 after.

### Per start

| # | load | cause | first_content | revealed | ready_req | ready | dropped_keys | key n | lat_ms | wait_ms | frame_ms | expired |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 3.60 | cap | 987 | 2695 | 2322 | 2680 | 0 | 1 | 764 | 4 | 21 | - |
| 2 | 3.31 | cap | 967 | 2668 | 1957 | 2222 | 0 | 1 | 611 | 4 | 20 | - |
| 3 | 3.87 | ready | 912 | 2493 | 1731 | 2078 | 0 | 1 | no line | - | - | - |
| 4 | 3.78 | cap | 958 | 2669 | 2177 | 2555 | 0 | 1 | 297 | 3 | 22 | - |
| 5 | 3.68 | cap | 952 | 2671 | 2325 | 2469 | 0 | 1 | 440 | 3 | 21 | - |
| 6 | 3.79 | cap | 906 | 2931 | 2107 | 2554 | 0 | 1 | 941 | 8 | 8 | - |
| 7 | 3.78 | cap | 972 | 2647 | 1872 | 2103 | 0 | 1 | 414 | 3 | 24 | - |
| 8 | 3.88 | cap | 990 | 2676 | 1923 | 2154 | 0 | 1 | 591 | 4 | 25 | - |
| 9 | 4.19 | cap | 933 | 2646 | 1964 | 2406 | 0 | 1 | 563 | 3 | 28 | - |
| 10 | 3.79 | cap | 956 | 2820 | 1963 | 2435 | 0 | 1 | 680 | 3 | 21 | - |
| 11 | 3.58 | cap | 967 | 2656 | 1792 | 2154 | 0 | 1 | 507 | 4 | 23 | - |
| 12 | 3.38 | cap | 940 | 2669 | 1907 | 2168 | 0 | 1 | 520 | 5 | 28 | - |
| 13 | 3.27 | cap | 878 | 2693 | 1913 | 2355 | 0 | 1 | 386 | 4 | 25 | - |
| 14 | 3.48 | cap | 941 | 2675 | 1982 | 2298 | 0 | 1 | 329 | 4 | 27 | - |
| 15 | 3.96 | cap | 961 | 2666 | 1978 | 2462 | 0 | 1 | 605 | 4 | 25 | - |

Start 3 wrote no key_lat line (key handled in 50 ms or less); it is counted as 50 in the summary.

## Summary S0 / S1 / S2 (n = 15 each, same box, same day, same base APK)

| metric | S0 (as installed, no profile, no compile) | S1 (embedded profile, forced compile) | S2 (forced compile of the S0-run JIT profile) |
|---|---|---|---|
| dexopt | speed-profile, reason=install (profiles empty) | speed-profile, reason=unknown | speed-profile, reason=unknown |
| first-key lat_ms median | 2578 | 520 | <= 50 (14 of 15 without a key_lat line) |
| first-key lat_ms p95 (rank 15 = max) | 3105 | 941 | 120 |
| min / max | 1766 / 3105 | 50 / 941 | <= 50 / 120 |
| revealed median | 3269 | 2669 | 1866 |
| first_content median | 1131 | 956 | 800 |
| cause | cap x15 | cap x14, ready x1 | ready (most) and cap |
| dropped_keys | 0 | 0 | 0 |
| load average (1 min) before starts | 3.18-4.11 | 3.27-4.19 | 2.89-4.43 |

Reading: the base build's S1 (what the shipped embedded profile gives after a compile) has p95 941 ms against the AC-4 gate of 199 ms, so the gate is not met by the base. S2 is far faster than S1, so the embedded baseline profile covers much less of the startup path than the JIT profile of a real launch.
