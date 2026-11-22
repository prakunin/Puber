# Device evidence: startup-first-input-on-tv verify round 4 (AC-6 compiled state, Manual check 4), HEAD 276ddbee

Result: NOT CHECKED. The probe stopped early: the KinoPub API still answered HTTP 502 for most requests, so 5 of 6 starts landed on the Home error state. Only 1 start reached Home content; the spec asks for 12 or more. The full protocol was not run.

## Device and build

- Device: TV box 192.168.1.110:5555 (KM6, Android 10), leased and released. Emulator not used. Package com.kino.puber.stage only.
- Build: the installed `devBenchmarkRelease` of tree 79afebd3. The on-device base.apk sha256 is efb00a3a18f27490d85ffa5bcd3c6caa3579edc592dc463f3d49a5adc1aa7ee9, equal to the freshly built app-dev-benchmarkRelease.apk. No reinstall, no compile by qa.
- Compiler filter (`dumpsys package com.kino.puber.stage`, Dexopt state) before and after the probe: `arm: [status=speed-profile] [reason=unknown]` (the conductor's compile).
- Box load average (1 min): 2.93 before, 3.89 after.
- Method as in rounds 2 and 3: on-device app_process injector (KEYCODE_DPAD_DOWN through InputManager.injectInputEvent), anchors A (launch +3 s) and B (first black-pixel exit at (20,72), injected at detection), C (detection +0.7 s); cold start = force-stop, 2.5 s, am start, 17 s per start. The injector files in /data/local/tmp were removed after the probe.

## Probe: landing per start

| # | anchor | landing | detail |
|---|---|---|---|
| 1 | A | Error | ready st=Error at 1504 ms |
| 2 | B | Content | ready at 10290 ms (first_content 10359, cause cap, revealed 2634, dropped_keys 0), no key_lat line, injected 3273 ms after launch |
| 3 | A | Error | err HTTP 502 (kd2.J), ready st=Error |
| 4 | C | Error | err HTTP 502, ready st=Error |
| 5 | A | Error | err HTTP 502, no ready line |
| 6 | B | Error | ready st=Error at 1545 ms |

Round 3 had the same picture (6 of 48 content starts). The one content start here took 10.3 s to first content, not comparable with round 2, which logged no first_content (its ready_req was 3.5-5.2 s).

## Per-anchor numbers

None. 1 content start in total (anchor B, no key_lat line, so no lat_ms or wait_ms). Round 2 as installed for comparison: A lat 3654 / 3957 (median / p95), wait 592 / 1036; B lat 3666.5 / 4330, wait 329.5 / 515; C lat 4068.5 / 4745, wait 259 / 500.

## AC-3 anchors, as realised

| anchor | target | realised | met |
|---|---|---|---|
| A | launch +3 s | launch +3.0 s (3022 / 3029 / 3041 ms) | injection yes, no content start (all Error) |
| B | reveal +300 ms | one content start, injected 3273 ms after launch, reveal mark 2634 ms after process start (not subtracted precisely) | not evaluated (n = 1) |
| C | reveal +1000 ms | no content start | NOT MET as specified (no data) |

## Not done

- 12 or more content starts and per-anchor lat_ms / wait_ms: not reachable while the API returns 502.
- Atrace of 3 starts: not run; it would profile the error path.
- Screenshot after the key: not taken (no start produced a content state worth distinguishing).
