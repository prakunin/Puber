# Device evidence: startup-first-input-on-tv verify round 1 (AC-5, AC-6), HEAD ed92c932

Plan Task 10; spec Manual checks 2 and 3 (check 4 not run).

## Device and build

- Device: TV box 192.168.1.110:5555 (KM6, Android 10, 1920x1080), leased and released. Emulator not used. Package com.kino.puber.stage only; com.kino.puber not touched.
- Build: `devBenchmarkRelease` of ed92c932 (base c506d0bd plus the amended AC-1/AC-2 telemetry), versionName 1.12.5-dev, versionCode 2090000000, apk sha256 2fa6e9e9c8eab670ce625573696f4c3326f1bba859411fb2a727b09d9e0814de. install-preserve: outcome installed, compatible_replace, destructive_action false, same signer 6b29181257cb...a83e. On-device base.apk sha256 equal to the built apk.
- `make DEVICE=192.168.1.110:5555 auth-save` could not run: the benchmark build is not debuggable ("run-as: package not debuggable ... has nothing to save yet"). No snapshot was taken; login was intact before the install (Home with hero and rows) and after it (Home content reached in all 18 starts, first_content in every startup line).
- Compiler filter of the as-installed set (`dumpsys package com.kino.puber.stage`, Dexopt state, recorded right after the install and again after the last start): `arm: [status=speed-profile] [reason=install]`. Nothing was compiled by hand. The speed-profile compile of check 4 was not authorised and not run: AC-6 compiled-state half NOT CHECKED. The as-installed state already reads speed-profile (installer-applied profile), which is not the owner-authorised compile and is not reported as that half.
- Session log: ON on the box (left on from the baseline); one file per start under the app's external files dir; only the `startup` and `key_lat` lines were read, no raw log is stored.

## Method

- Cold start = `am force-stop`, 2.5 s, `am start -W`; one uncounted warm-up start, then 15 counted starts rotating anchors A, B, C (5 each), then 3 atrace starts. One `input keyevent KEYCODE_DPAD_DOWN` per start. Every start waits 17 s after launch before the next force-stop (reveal at most about 4.4 s, so at least 12 s after the reveal; the 5 s bound and the 5 s sink drain are covered).
- Anchor A: injection requested at launch +2.44 s (`input` takes 0.5-1.6 s from spawn to the end of the command, the key reaches the app later). Anchors B and C: injection right after a real-time pixel poll (raw `screencap`, pixel at 20,72 leaving black) sees the rail, and 440 ms after that. "inj ms after launch" is the host time when the `input` command returned (launch = host time before `am start`); "inj - revealed" subtracts the telemetry `revealed` mark (ms since process start; the process-start offset of about 0.1-0.3 s is not subtracted).
- Atrace starts T1-T3: `atrace --async_start` (gfx view am wm input sched dalvik res, app tag) before the launch, key at launch +5 s (input returned at 5.7-6.1 s), stopped at launch +17 s, text trace parsed for B/E slices on the main thread (tid = pid). The traces are not stored (about 32 MB each); only the summaries below are.
- `-` = field absent from the line. All marks in ms since process start. A start with "dropped under splash" had its key dropped by the splash key filter (`dropped_keys` 1), so no `key_lat` line is expected for it.

## Per start (AC-5 and AC-6)

| # | anchor | cause | first_content | ready_req | ready | ready_in_flight | ready_drained | revealed | ready - revealed | ready_req - first_content | in_flight | dropped_keys | inj ms after launch | inj - revealed | lat_ms | wait_ms | frame_ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | A | cap | 1183 | 3274 | 4471 | 1 | 4523 | 3882 | 589 | 2091 | 6 | 1 | 3618 | -264 | dropped under splash, no line | - | - |
| 2 | B | cap | 1243 | 5430 | 6265 | 0 | - | 3355 | 2910 | 4187 | 12 | 0 | 6520 | 3165 | 3891 | 300 | 681 |
| 3 | C | cap | 1217 | 5177 | 5786 | 1 | 5803 | 3274 | 2512 | 3960 | 12 | 0 | 6616 | 3342 | 3070 | 4 | 86 |
| 4 | A | cap | 1161 | 2525 | 3356 | 0 | - | 3263 | 93 | 1364 | 4 | 1 | 3534 | 271 | dropped under splash, no line | - | - |
| 5 | B | cap | 1231 | 4765 | 5800 | 0 | - | 3188 | 2612 | 3534 | 8 | 0 | 6409 | 3221 | 3738 | 10 | 171 |
| 6 | C | cap | 1368 | 4655 | 5349 | 0 | - | 3132 | 2217 | 3287 | 8 | 0 | 6570 | 3438 | 3331 | 23 | 158 |
| 7 | A | cap | 1157 | 4361 | 5139 | 0 | - | 4359 | 780 | 3204 | 10 | 0 | 4570 | 211 | 2848 | 124 | 1971 |
| 8 | B | cap | 1419 | 4090 | 5219 | 0 | - | 3890 | 1329 | 2671 | 12 | 0 | 6285 | 2395 | 2628 | 4 | 69 |
| 9 | C | cap | 1239 | 5115 | 5864 | 0 | - | 3194 | 2670 | 3876 | 8 | 0 | 6791 | 3597 | 3046 | 5 | 77 |
| 10 | A | cap | 1122 | 4360 | 5155 | 0 | - | 4359 | 796 | 3238 | 11 | 0 | 4669 | 310 | 3039 | 200 | 1976 |
| 11 | B | cap | 1251 | 4399 | 5585 | 1 | 5688 | 3246 | 2339 | 3148 | 8 | 0 | 5911 | 2665 | 3582 | 130 | 638 |
| 12 | C | cap | 1198 | 4683 | 5511 | 0 | - | 3982 | 1529 | 3485 | 12 | 0 | 6773 | 2791 | 2494 | 4 | 83 |
| 13 | A | cap | 1199 | 3263 | 4360 | 2 | 4409 | 3802 | 558 | 2064 | 7 | 1 | 3586 | -216 | dropped under splash, no line | - | - |
| 14 | B | cap | 1233 | 5354 | 6112 | 0 | - | 3417 | 2695 | 4121 | 12 | 0 | 6236 | 2819 | 3522 | 358 | 616 |
| 15 | C | cap | 1220 | 4393 | 5541 | 2 | 5739 | 3229 | 2312 | 3173 | 8 | 0 | 6392 | 3163 | 3362 | 4 | 90 |
| T1 | key at launch +5 s (atrace) | cap | 1342 | 3528 | 3963 | 0 | - | 3790 | 173 | 2186 | 16 | 0 | 6128 | 2338 | 3332 | 6 | 133 |
| T2 | key at launch +5 s (atrace) | cap | 1200 | 4162 | 4838 | 0 | - | 3622 | 1216 | 2962 | 9 | 0 | 5862 | 2240 | 2434 | 5 | 93 |
| T3 | key at launch +5 s (atrace) | cap | 1276 | 2439 | 3934 | 0 | - | 3826 | 108 | 1163 | 2 | 0 | 5682 | 1856 | 1712 | 4 | 69 |

## Summary

- Starts: 15 counted plus 3 atrace, 18 of 18 with exactly one `startup` line (file per start; no start without a line, none with two). Cause `cap` in 18 of 18; no start revealed by `ready`.
- `ready` present in 18 of 18 starts (all land on Home content); `ready_req` present in 18 of 18; `ready_drained` present exactly where `ready_in_flight` > 0 (counted starts 1, 3, 11, 13, 15) and absent where it is 0.
- `ready - revealed` (15 counted starts) min / median / max: 93 / 2217 / 2910 ms; always positive: `ready` takes effect after the cap reveal. `ready_req - first_content` min / median / max: 1364 / 3238 / 4187 ms.
- Baseline 5fb5de17 for comparison: `ready` absent in 36 of 36, first-key `lat_ms` median 3186-3259 ms per anchor, p95 3628-4199 ms, `wait_ms` absent.
- `0 <= wait_ms <= lat_ms`: holds on every one of the 15 `key_lat` lines; no `expired` line and no `key_lat` line without `wait_ms`.

Per anchor, first-key latency of the counted starts (ms; p95 is nearest rank, equal to the maximum for n <= 20):

| anchor | n lat lines | lat_ms median | lat_ms p95 | wait_ms median | wait_ms p95 | starts with key dropped under splash |
|---|---|---|---|---|---|---|
| A | 2 of 5 | 2943.5 | 3039 | 162 | 200 | 3 |
| B | 5 of 5 | 3582 | 3891 | 130 | 358 | 0 |
| C | 5 of 5 | 3070 | 3362 | 4 | 23 | 0 |
| all | 12 of 15 | 3200.5 | 3891 | 16.5 | 358 | 3 |

Atrace starts T1-T3 (key at launch +5 s): lat_ms 3332, 2434, 1712; wait_ms 6, 5, 4.

## Limits

- The pixel poll lagged the real reveal: B and C keys were injected 2.4-3.4 s after the telemetry `revealed` mark (column "inj - revealed"), not +300 ms and +1000 ms; the baseline poll had the same limit but lagged less (press-R median 1.3 s and 1.9 s). Anchor A keys landed 0.2-0.3 s after `revealed` in the two that were answered, and under the splash in three. Across anchors the latency stays 2.5-3.9 s, the same range as the baseline.
- Realised A injections (3.5-4.7 s after launch) are later than the baseline's, so three of five A keys met the splash (dropped, counted in `dropped_keys`).
- n = 5 per anchor: medians and p95 are descriptive only. Anchor A has two latency lines.
- Not checked: focus position after the key (no screenshots were taken in this set; the evidence is telemetry only).
- Atrace slices are counts over main-thread slices (no trace stored).

## Main-thread slices over 100 ms (3 atrace starts)

Start T1 (main thread, pid 12420, offsets from bindApplication, 46 slices over 100 ms; name: count, longest ms):
- Choreographer#doFrame: 13, 815
- traversal: 7, 690
- draw: 5, 690
- Record View#draw(): 5, 666
- AndroidOwner:measureAndLayout: 4, 582
- animation: 4, 303
- Recomposer:recompose: 3, 299
- Compose:recompose: 2, 214
- measure: 1, 188
- AndroidOwner:onMeasure: 1, 188
- bindApplication: 1, 112

Start T2 (main thread, pid 12691, offsets from bindApplication, 45 slices over 100 ms; name: count, longest ms):
- Choreographer#doFrame: 12, 488
- traversal: 6, 391
- draw: 5, 391
- Record View#draw(): 5, 382
- AndroidOwner:measureAndLayout: 6, 325
- animation: 5, 243
- Recomposer:recompose: 4, 239
- Compose:recompose: 1, 177
- bindApplication: 1, 115

Start T3 (main thread, pid 12851, offsets from bindApplication, 38 slices over 100 ms; name: count, longest ms):
- Choreographer#doFrame: 9, 773
- traversal: 6, 697
- draw: 5, 697
- Record View#draw(): 5, 686
- AndroidOwner:measureAndLayout: 4, 589
- animation: 4, 254
- Recomposer:recompose: 3, 250
- Compose:recompose: 1, 183
- bindApplication: 1, 116

## AC-6 compiled state (check 4)

NOT CHECKED: `cmd package compile -m speed-profile -f com.kino.puber.stage` is not authorised, qa does not run it.
