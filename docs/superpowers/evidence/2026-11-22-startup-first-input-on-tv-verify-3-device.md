# Device evidence: startup-first-input-on-tv verify round 3 (AC-6 compiled state, Manual check 4), HEAD ce3d859e

Result: NOT CHECKED. The KinoPub API answered HTTP 502 for most requests during the whole window, so 42 of 48 starts landed on the Home error state and wrote no `startup` line. 6 starts reached Home content; the spec asks for 12 or more.

## Device and build

- Device: TV box 192.168.1.110:5555 (KM6, Android 10), leased and released. Emulator not used. Package com.kino.puber.stage only.
- Build: `devBenchmarkRelease` of ce3d859e (same tree as round 2). A fresh build gave apk sha256 efb00a3a18f27490d85ffa5bcd3c6caa3579edc592dc463f3d49a5adc1aa7ee9; the on-device base.apk has the same sha256. No reinstall.
- Compiler filter (`dumpsys package com.kino.puber.stage`, Dexopt state) before the first set, after it, and after the second set: `arm: [status=speed-profile] [reason=unknown]` (the conductor's compile; qa did not compile).
- Box load average (1 min): 3.28 before set 1, 3.42 after set 1, 2.67 at the end (round 2 after its set: 5.69).
- Method as in round 2: on-device `app_process` injector (KEYCODE_DPAD_DOWN, `InputManager.injectInputEvent`), anchors A (launch +3 s), B (first black-pixel exit at (20,72), injected at detection), C (detection +0.7 s), cold start = force-stop, 2.5 s, `am start`, 17 s per start. Two sets of 24 starts (12 A, 6 B, 6 C each), the second after a 4-minute gap.

## Landing per start

C = Home content with a `startup` line, E = Home `ready` state Error, - = no `ready` line before the session ended.

- Set 1: 1A:E 2B:E 3C:E 4A:C 5A:- 6B:E 7C:E 8A:C 9A:E 10B:E 11C:E 12A:- 13A:E 14B:C 15C:E 16A:E 17A:E 18B:C 19C:E 20A:E 21A:C 22B:E 23C:E 24A:E
- Set 2: 1A:E 2B:E 3C:E 4A:E 5A:E 6B:E 7C:E 8A:- 9A:E 10B:E 11C:E 12A:E 13A:E 14B:C 15C:E 16A:E 17A:E 18B:E 19C:E 20A:E 21A:- 22B:E 23C:- 24A:E
- 19 sessions carry an `err` line, all HTTP 502 (`kd2.J` API call). A 9-start probe afterwards (one start every 25 s, 9 s wait each) read Error 4 times and no ready line 5 times; none showed Content.

## Starts that reached Home content

`-` = absent. inj - revealed is the injection minus the telemetry `revealed` mark on one clock; detect - revealed is the same for the B detector.

| set.# | anchor | cause | first_content | ready_req | ready | ready_in_flight | ready_drained | revealed | inj after launch | inj - revealed | detect - revealed | key_lat | dropped_keys |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1.4 | A | cap | 3485 | 4397 | 4490 | 12 | 5092 | 2630 | 3046 | 226 | - | no line | 0 |
| 1.8 | A | cap | 8546 | - | - | - | - | 2627 | 3043 | 219 | - | no line | 0 |
| 1.14 | B | cap | 4879 | 5245 | 5294 | 3 | 5486 | 2642 | 3235 | 394 | 322 | no line | 0 |
| 1.18 | B | cap | 4726 | 5033 | 5112 | 4 | 5453 | 2641 | 3027 | 185 | 123 | no line | 0 |
| 1.21 | A | cap | 4811 | 5173 | 5243 | 4 | 5531 | 2635 | 3046 | 201 | - | no line | 0 |
| 2.14 | B | cap | 4298 | 4830 | 4878 | 4 | 5705 | 2629 | 3085 | 252 | 191 | no line | 0 |

`ready - revealed` 1860 / 2652 / 2471 / 2608 / 2249 ms for rows 1.4 / 1.14 / 1.18 / 1.21 / 2.14; `ready_req - first_content` 912 / 366 / 307 / 362 / 532 ms.

Starts 1.4 / 1.8 / 1.21 are anchor A (3 starts), 1.14 / 1.18 / 2.14 anchor B (3 starts), none for C. Row 1.8 has no `ready_req`, `ready` or `ready_drained` fields (absent from its startup line; first_content 8546). In all 6 the reveal came at 2627-2642 ms, cause `cap`, and the key was handled with no `key_lat` line (50 ms or less, or not handled; no screenshot taken, so the two are not told apart). No `expired`, no dropped key.

## Per-anchor numbers

No `key_lat` line exists in the compiled set, so lat_ms and wait_ms medians and p95 cannot be computed for any anchor (A: 3 content starts, B: 3, C: 0). Round 2 as installed, for comparison: A lat 3654 / 3957 (median / p95), wait 592 / 1036; B lat 3666.5 / 4330, wait 329.5 / 515; C lat 4068.5 / 4745, wait 259 / 500.

## AC-3 anchors, as realised (content starts only)

| anchor | target | realised | met |
|---|---|---|---|
| A | launch +3 s | 3027-3046 ms after launch (content starts); after the reveal mark 201-226 ms | yes, n = 3 |
| B | reveal +300 ms | 185 / 252 / 394 ms after the mark, detector 123 / 191 / 322 ms | yes, n = 3 (round 2 realised 1220-2063 ms) |
| C | reveal +1000 ms | no content start | NOT MET as specified (no data) |

## Not done

- Atrace of 3 starts: not run; Home was in the error state in most starts, so main-thread slices would describe the error path, not the Home start.
- Focus or screenshot after the key: not taken.
- 12 or more content starts and per-anchor lat_ms / wait_ms: not reachable while the API returns 502.
