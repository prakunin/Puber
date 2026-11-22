# Device evidence: startup-first-input-on-tv verify round 2 (AC-6 re-measure), HEAD 9d454407

Re-measure after fix round 1 (triage-1 row 1). AC-6 as-installed half only; the compiled half stays NOT CHECKED (no authorisation).

## Device and build

- Device: TV box 192.168.1.110:5555 (KM6, Android 10, armeabi-v7a), leased and released. Emulator not used. Package com.kino.puber.stage only.
- Build: `devBenchmarkRelease` of 9d454407, versionName 1.12.5-dev, versionCode 2090000000, apk sha256 efb00a3a18f27490d85ffa5bcd3c6caa3579edc592dc463f3d49a5adc1aa7ee9. install-preserve: outcome installed, compatible_replace, destructive_action false, same signer 6b29181257cb...a83e. On-device base.apk sha256 equal to the built apk.
- Compiler filter (`dumpsys package com.kino.puber.stage`, Dexopt state), after the install and again after the last start: `arm: [status=speed-profile] [reason=install]`. Nothing compiled by hand.
- Box load average (1 min) after the set: 5.69 (idle about 3; the same range as the baseline's 4.8-6.1).

## Trigger and detector

- Trigger: a long-lived on-device `app_process` helper (a 40-line dex, test scaffolding in /data/local/tmp, removed afterwards) calls `InputManager.injectInputEvent` for KEYCODE_DPAD_DOWN (DOWN then UP, source KEYBOARD, device id -1, event time = `uptimeMillis`), started once per set and warmed before the first start. A device-side script writes a trigger file; the helper polls for it every 1 ms. Trigger-to-injection 4 and 6 ms in a standalone test, against 0.5-1.6 s for `input keyevent`. Direct `sendevent` to /dev/input was refused by SELinux (Permission denied) and not used.
- Every time is the box's wall clock. `injection` is the helper's `currentTimeMillis` at injection. Process start = session `wall_ms` minus its `t` in the app's telemetry file, so "inj - revealed" is the injection minus the telemetry `revealed` mark on one clock. "after launch" is the injection minus the host-script time just before `am start` (process start is 0.2 s later).
- Cold start = `am force-stop`, 2.5 s, `am start` (not `-W`); every start runs 17 s after launch before the next force-stop. No warm-up start.
- Anchor A: injection at launch +3.0 s (busy wait on the device clock). Anchors B and C: the reveal detector is a pair of staggered on-device loops (started at launch +2.0 s, 0.15 s apart) running `screencap`, cutting the pixel at (20,72) and testing it against opaque black. B injects at the first detection; C injects 0.7 s after it. One sample takes 0.2-0.65 s.
- Reveal marks are the telemetry `revealed` of the same start; there is no logcat marker for the reveal in the build.
- `-` = absent. A start with "no line" and 0 dropped keys has no `key_lat` line: the latency was 50 ms or less, or the key was not handled; no screenshot was taken, so the two are not told apart.

## Per start

| # | anchor | cause | ready_req | ready | ready_in_flight | ready_drained | revealed | inj ms after launch | inj - revealed | detect - revealed | lat_ms | wait_ms | dropped_keys |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | A | cap | 4466 | 5171 | 0 | - | 3852 | 3156 | -915 | - | dropped under splash | - | 1 |
| 2 | B | cap | 4641 | 5566 | 0 | - | 3073 | 5341 | 2063 | 1835 | 3562 | 136 | 0 |
| 3 | C | cap | 5355 | 6308 | 1 | 6412 | 3283 | 5779 | 2290 | 1456 | 4130 | 415 | 0 |
| 4 | A | cap | 4637 | 5638 | 0 | - | 2916 | 3107 | -18 | - | 3506 | 481 | 0 |
| 5 | B | cap | 4410 | 5410 | 2 | 5499 | 3277 | 5222 | 1738 | 1558 | 2487 | 90 | 0 |
| 6 | C | cap | 4225 | 5310 | 0 | - | 3903 | 5897 | 1800 | 931 | 3639 | 137 | 0 |
| 7 | A | cap | 4560 | 5337 | 0 | - | 3924 | 3090 | -1039 | - | dropped under splash | - | 1 |
| 8 | B | cap | 4857 | 6012 | 0 | - | 4079 | 5505 | 1220 | 1129 | 4301 | 410 | 0 |
| 9 | C | cap | 4787 | 5695 | 0 | - | 3194 | 6015 | 2628 | 1776 | 4357 | 376 | 0 |
| 10 | A | cap | 3521 | 4026 | 2 | 4264 | 3063 | 3106 | -166 | - | no line | - | 0 |
| 11 | B | cap | 4923 | 5991 | 1 | 6046 | 3326 | 5353 | 1813 | 1731 | 3683 | 515 | 0 |
| 12 | C | cap | 4869 | 5858 | 0 | - | 3179 | 6089 | 2712 | 1846 | 4745 | 500 | 0 |
| 13 | A | cap | 3747 | 4465 | 0 | - | 3238 | 3125 | -320 | - | no line | - | 0 |
| 14 | A | cap | 5103 | 5922 | 0 | - | 3251 | 3099 | -351 | - | 3788 | 1036 | 0 |
| 15 | A | cap | 5203 | 6018 | 0 | - | 3299 | 3076 | -426 | - | dropped under splash | - | 1 |
| 16 | A | cap | 4943 | 5906 | 0 | - | 2868 | 3068 | -12 | - | 3957 | 861 | 0 |
| 17 | A | cap | 3608 | 4028 | 2 | 4265 | 3128 | 3109 | -225 | - | no line | - | 0 |
| 18 | A | cap | 3877 | 4260 | 0 | - | 3321 | 3134 | -397 | - | no line | - | 0 |
| 19 | A | cap | 4250 | 4699 | 1 | 4718 | 2714 | 3092 | 175 | - | no line | - | 0 |
| 20 | A | cap | 3810 | 4185 | 5 | 4877 | 3292 | 3138 | -348 | - | no line | - | 0 |
| 21 | A | cap | 4530 | 5570 | 0 | - | 3284 | 3076 | -405 | - | 1647 | 290 | 0 |
| 22 | A | cap | 3827 | 4589 | 0 | - | 3202 | 3102 | -302 | - | no line | - | 0 |
| 23 | A | cap | 5270 | 6003 | 0 | - | 3468 | 3109 | -565 | - | dropped under splash | - | 1 |
| 24 | A | cap | 3747 | 4139 | 2 | 4651 | 3190 | 3064 | -341 | - | no line | - | 0 |
| 25 | A | cap | 4646 | 5803 | 0 | - | 2955 | 3108 | -51 | - | 3656 | 484 | 0 |
| 26 | A | cap | 4723 | 5616 | 0 | - | 4722 | 3134 | -1824 | - | 3587 | 592 | 0 |
| 27 | A | cap | 5025 | 5805 | 0 | - | 3183 | 3077 | -317 | - | 3841 | 959 | 0 |
| 28 | A | cap | 4042 | 4678 | 0 | - | 3610 | 3070 | -741 | - | no line | - | 0 |
| 29 | A | cap | 4661 | 5598 | 0 | - | 4660 | 3062 | -1800 | - | 3654 | 675 | 0 |
| 30 | A | cap | 4659 | 5875 | 0 | - | 2953 | 3131 | -22 | - | 3537 | 443 | 0 |
| 31 | B | cap | 4350 | 5257 | 0 | - | 3208 | 4862 | 1432 | 1275 | 3650 | 323 | 0 |
| 32 | C | cap | 4829 | 5690 | 0 | - | 3173 | 5714 | 2337 | 1526 | 3953 | 142 | 0 |
| 33 | B | cap | 4912 | 6077 | 0 | - | 4133 | 5640 | 1303 | 1188 | 4330 | 336 | 0 |
| 34 | C | cap | 4831 | 5881 | 1 | 5943 | 3248 | 6089 | 2644 | 1821 | 4007 | 46 | 0 |

## Summary

`startup` line: one per start in all 34 starts, cause `cap` in all, `ready_req` and `ready` present in all, `ready_drained` present exactly where `ready_in_flight` > 0 (9 starts). `0 <= wait_ms <= lat_ms` on every one of the 21 `key_lat` lines; none `expired`.

| anchor | starts | lat lines | starts dropped under splash / no line | lat_ms median | lat_ms p95 | wait_ms median | wait_ms p95 |
|---|---|---|---|---|---|---|---|
| A | 22 | 9 | 4 / 9 | 3654 | 3957 | 592 | 1036 |
| B | 6 | 6 | 0 / 0 | 3666.5 | 4330 | 329.5 | 515 |
| C | 6 | 6 | 0 / 0 | 4068.5 | 4745 | 259 | 500 |

p95 is the nearest rank. Descriptive only (observation, no threshold).

## AC-3 anchors, as realised

| anchor | target | realised injection | met |
|---|---|---|---|
| A | launch +3 s | after launch 3062 / 3104 / 3156 ms (min / median / max); after the reveal mark -1824 / -344.5 / 175 ms | yes, A lat lines n = 9 |
| B | reveal +300 ms | after the reveal mark 1220 / 1585 / 2063 ms; detector fired 1129 / 1416.5 / 1835 ms after the mark | NOT MET as specified |
| C | reveal +1000 ms | after the reveal mark 1800 / 2482.5 / 2712 ms; detector fired 931 / 1651 / 1846 ms after the mark | NOT MET as specified |

Round 1 realised B 2395-3342 ms and C 2791-3597 ms after the mark; the trigger removed the spawn cost (injection at detection + about 5 ms), the rest is detection. The detector cannot fire earlier than about +0.9 s: the `revealed` mark is set on the main thread, and the first frame that shows the reveal is itself slow (`frame_ms` of B starts 677-881 ms, one `Choreographer: Skipped 35 frames` plus an `OpenGLRenderer` Davey of 726 ms seen around the mark in a logcat probe), so the pixel stays black until that frame is drawn. Nothing outside the process precedes the mark, and the mark is 2.7-4.7 s after process start with no earlier observable. B and C therefore land at about +1.2-2.1 s and +1.8-2.7 s; they cannot be brought to +300 / +1000 ms from outside the app.

Observation at anchor A: keys injected before the reveal mark (median -344.5 ms, down to -1824 ms) were often not counted in `dropped_keys` (4 of 22 starts dropped); the rest produced a `key_lat` line of 1.6-4.0 s (wait_ms 0.3-1.0 s) or none.

## Limits

- Anchors B and C not met as specified (above). AC-6 per-anchor B and C numbers are for keys 1.2-2.7 s after the mark.
- Not re-run: the 3 atrace starts of AC-6 (round 1 slices, build ed92c932, stand; the fix round changed gate reporting and test files only) and the compiled-state half (not authorised).
- No focus or screenshot check after the key; the evidence is telemetry only.
