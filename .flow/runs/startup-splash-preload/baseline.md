# Baseline: startup-splash-preload (perf), unchanged base a22c03bc

Mode baseline, track perf. Worktree /home/user1/orca/workspaces/Puber/startup-splash-preload, no code changes. Round 1, 2026-11-21.

## Device and builds

- Device: emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU, logged in), leased and released. The emulator was not running at
  the start; it was started with `-no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect`. App data and login were intact.
- Host load (1-minute load average, 8 cores): 6.6-9.5 during the measured runs, so the host was loaded and absolute numbers are inflated
  and noisy. Compare only against a re-measurement on the same emulator under similar load.
- devDebug: `app-dev-debug.apk` sha256 ea6b5176...09d86, versionName 1.12.3-dev, installed with install-preserve (outcome installed,
  compatible_replace, destructive_action false), on-device base.apk sha256 equal.
- devBenchmarkRelease (R8, debug-signed): `app-dev-benchmarkRelease.apk` sha256 6cabdc4b...1ffeb. M9 check passed before install: signer
  6b29181257cb...a83e and versionCode 2090000000 equal to the installed package, no signer change, no downgrade; install-preserve outcome
  installed, compatible_replace, on-device sha256 equal. It is left installed.
- (A first combined `assembleDevDebug assembleDevBenchmarkRelease` call failed in 6m53s; separate calls succeeded. Not investigated, not a product finding.)
- Telemetry was enabled for the measurement by writing `enabled_at_wall_ms` to the app's `telemetry_preferences` (run-as on the debug build); it expires after 7 days and was left on.
  Telemetry-on adds probe overhead to both builds equally. Nothing was uninstalled, cleared or downgraded.

## Method

Cold start = `am force-stop`, 3 s, `am start -W -n com.kino.puber.stage/com.kino.puber.MainActivity`. 8 cold starts per build, the first dropped, 7 reported.
Marks (ms since process start) come from the telemetry `startup` line: `app_composed`, `first_content` (no `revealed` exists at base). `Home ready` is the
`ready` Home Content `ms` (since Home start). `TotalTime` is `am start -W`.

**key_lat script (test-design.md, Perf track, Metric 2):** after `first_content` (anchor: the `startup` line appears in the session file, polled every 0.1 s
in one `adb shell` script), 6 key events at 500 ms spacing: `input keyevent 22, 21, 22, 21, 22, 21` (DPAD_RIGHT/LEFT alternating), all inside the first 3 s after the anchor.
No keys were sent before the anchor; an earlier measurement with a different script was not made. `key_lat` is the telemetry `key_lat` event, which the app
writes only when latency exceeds 50 ms (`SLOW_MS`), so a key without a line is at most 50 ms. Frames: `dumpsys gfxinfo <pkg> reset` at the anchor, dump 3 s of keys plus 2 s later (about 5 s window).

## Raw runs

Columns: run, load before, TotalTime ms, app_composed ms, first_content ms, Home ready ms, key_lat ms (>50 only, of 6 keys), frames, janky, frame time p50/p90/p95/p99 ms.

### devDebug
| Run | Load | TotalTime | app_composed | first_content | Home ready | key_lat | Frames | Janky | p50/p90/p95/p99 |
|---|---|---|---|---|---|---|---|---|---|
| 1 (dropped) | 9.47 | 2291 | 2589 | 6162 | 2605 | none >50 | 54 | 54 (100%) | 133/200/250/300 |
| 2 | 9.44 | 2065 | 2156 | 5120 | 2048 | 1701 | 59 | 48 (81.36%) | 93/250/400/1250 |
| 3 | 9.13 | 1903 | 1892 | 5470 | 2682 | 1932, 1203 | 61 | 49 (80.33%) | 85/450/600/1250 |
| 4 | 8.14 | 1967 | 2102 | 4325 | 1285 | 2095 | 47 | 43 (91.49%) | 89/300/700/1850 |
| 5 | 6.75 | 1826 | 1868 | 5025 | 2302 | 1767 | 54 | 53 (98.15%) | 121/400/1000/1400 |
| 6 | 6.94 | 2413 | 2546 | 5221 | 1703 | 1287, 1643 | 34 | 31 (91.18%) | 121/750/1050/1050 |
| 7 | 8.44 | 1791 | 1723 | 4242 | 1675 | none >50 | 68 | 58 (85.29%) | 81/200/400/750 |
| 8 | 7.41 | 1981 | 1936 | 4589 | 1591 | 1824 | 36 | 34 (94.44%) | 150/700/850/1250 |

### devBenchmarkRelease
| Run | Load | TotalTime | app_composed | first_content | Home ready | key_lat | Frames | Janky | p50/p90/p95/p99 |
|---|---|---|---|---|---|---|---|---|---|
| 1 (dropped) | 7.71 | 788 | 629 | 2110 | 906 | 103, 104 | 79 | 69 (87.34%) | 73/97/101/113 |
| 2 | 8.23 | 914 | 737 | 2878 | 1554 | 110, 153 | 79 | 71 (89.87%) | 73/93/109/121 |
| 3 | 7.94 | 573 | 465 | 1777 | 750 | 90, 72 | 90 | 68 (75.56%) | 61/81/89/101 |
| 4 | 6.84 | 729 | 572 | 2672 | 1513 | 61, 271, 129, 137 | 49 | 49 (100%) | 150/200/250/350 |
| 5 | 7.57 | 765 | 703 | 2709 | 1421 | 207, 112, 162 | 60 | 55 (91.67%) | 105/150/150/200 |
| 6 | 6.63 | 593 | 505 | 2144 | 1070 | 159, 121, 199 | 55 | 54 (98.18%) | 133/150/200/200 |
| 7 | 6.84 | 848 | 658 | 2202 | 957 | 94, 107 | 73 | 64 (87.67%) | 81/109/117/129 |
| 8 | 7.95 | 751 | 661 | 2154 | 901 | 111, 106 | 69 | 62 (89.86%) | 85/125/150/200 |

## Summary (runs 2-8, n = 7 per build)

| Metric | devDebug median (range) | devBenchmarkRelease median (range) |
|---|---|---|
| `am start -W` TotalTime ms | 1967 (1791-2413) | 751 (573-914) |
| `app_composed` ms | 1936 (1723-2546) | 658 (465-737) |
| **`first_content` ms (AC-13 anchor)** | **5025** (4242-5470) | **2202** (1777-2878) |
| Home ready ms (since Home start) | 1703 (1285-2682) | 1070 (750-1554) |
| Keys with key_lat > 50 ms, of 42 | 8/42 | 18/42 |
| key_lat of the slow keys, median / max ms | 1734 / 2095 | 116.5 / 271 |
| **key_lat p95 over all 42 keys (unlogged counted as 50) ms** | **1824** | **199** |
| key_lat p95 over the slow keys only ms | 2095 | 271 |
| Frames in the ~5 s window, median | 54 | 69 |
| Janky percent, median (software GPU: nearly all frames janky, a gross check only) | 91.18 | 89.87 |
| Frame time p50 / p90 / p95 / p99 ms, medians | 93 / 400 / 700 / 1250 | 85 / 125 / 150 / 200 |
| Host load average during runs | 6.75-9.44 | 6.63-8.23 |

Slow key_lat values, devDebug: 1701,1932,1203,2095,1767,1287,1643,1824. devBenchmarkRelease: 110,153,90,72,61,271,129,137,207,112,162,159,121,199,94,107,111,106.

## Targets for the after measurement (brief Q1 / test-design Perf track)

- devBenchmarkRelease: median `revealed` <= 2202 + 300 = 2502 ms, and key_lat p95 (same 6-key script from `revealed`, same counting) < 199 ms.
- devDebug: reported, no threshold.

## Caveats

- Emulator, software GPU, loaded host: spread of first_content is about 30 percent in both builds (release 1777-2878); a difference under that spread is not evidence.
- Keys are sent after `first_content`, which at base fires when Home's first content state appears, while Home is still assembling (hero focus, remaining rows, device-info and TV home sync). That is the intended "before" load for key_lat.
- key_lat is not TV key_lat (no real-TV baseline). The run's frames window is about 5 s, not a 10 s telemetry `frames` window, which closes only on a later frame.
