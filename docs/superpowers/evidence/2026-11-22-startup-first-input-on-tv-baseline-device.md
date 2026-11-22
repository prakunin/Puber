# Device evidence: startup-first-input-on-tv baseline (AC-3), phase-A f48beb86

Same numbers as `.flow/runs/startup-first-input-on-tv/baseline.md`. Plan Task 6; spec Manual check 1.

## Device and build

- Device: TV box 192.168.1.110:5555 (KM6, Android 10, 1920x1080), leased and released. Emulator not used. Package com.kino.puber.stage only; com.kino.puber untouched.
- Build: `devBenchmarkRelease` of f48beb86 (base c506d0bd + AC-1/AC-2 telemetry), versionName 1.12.5-dev, versionCode 2090000000, apk sha256 9966eb8951c2294c597a4c0ed51dd600c4c6f58cdfab0cfbf9c519dccfff159c. install-preserve: outcome installed, compatible_replace, destructive_action false, same signer 6b29181257cb...a83e; on-device base.apk sha256 equal. `auth-save` ran before the first install; login intact at the end (Home with hero and rows shown after the last install).
- Telemetry session log: was OFF on the box; switched ON through Settings > For developers > session log (the only app-state change; it switches itself off after 7 days; one session file per start under the app's external files dir). Left ON for the next measurement.
- 8428c946 reference: `git archive` into a scratch dir, `devBenchmarkRelease` built there with its own tools/agentw, 1.12.3-dev, apk sha256 d67e3cb9d70c11388b53ed6722e7585b558e5ef16acd242ebc6f27a850647749, installed over the phase-A build (not a downgrade: both versionCode 2090000000, outcome installed, compatible_replace). Phase-A APK reinstalled afterwards (sha256 9966eb89... on device).

## Method

- Cold start = `am force-stop`, 2.5 s, `screenrecord` (1280x720, started 1.2 s before launch) and `am start -W`. One uncounted warm-up start, then 36 counted starts, anchors rotated A, B, C, A, ... (12 starts each). One `input keyevent KEYCODE_DPAD_DOWN` per start.
- Visible reveal is read in real time by a pixel poll (`screencap`, rail pixel leaves splash black, about 0.3 s per sample) and afterwards from the recording. The poll detected the reveal 0.46-1.68 s after the visible fade start (anchor B injects at detection, so its press-visible is that lag plus the injection delay), so B and C do not hit their nominal offsets. The press time `press-visible` is the injection stamp minus the visible fade start read from the recording (first frame with the splash fading or gone, so an upper bound of the true reveal when the recorder skipped frames); `press-R` is the same stamp minus the telemetry `revealed` mark.
- Anchor A: injection at launch +2.44 s (`input` takes 0.53-0.59 s from spawn to dispatch on an idle box, so the key reaches the app at about launch +3 s). Anchor B: injection at the first poll sample after the visible reveal (nominal "reveal +300 ms"; the earliest the box can inject it). Anchor C: injection 440 ms after that sample (nominal "reveal +1000 ms"). Realised offsets are in the table and the summary.
- Per start: `startup` telemetry line (cause, ready, revealed, img_idle, in_flight, ready_in_flight, ready_drained, first_content, dropped_keys) and the first `key_lat` line for the key (no line = 50 ms, `expired` = fail; none expired). `-` = field absent from the line. Offsets in ms, marks in ms since process start.
- Hero drawn at fade end: the first recorded frame with the splash gone, judged by eye on 320x180 stills (not stored: account rows). yes = backdrop photo drawn; no = skeleton hero or hero scrolled out of view (layout column).
- Box load average (1 min) before each start: 4.81-6.11 (idle about 3). Instrumentation (screenrecord plus poll) vs none: 8 starts without it gave `revealed` 2965-4344 (36 instrumented: 2719-4260) and key_lat 2845-3345 ms, so the recorders do not explain the numbers.

## Per start

| # | anchor | load | cause | ready | revealed | img_idle | in_flight | ready_in_flight | ready_drained | first_content | dropped_keys | press-R | press-visible | key_lat | hero | layout |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | A | 4.81 | cap | - | 2744 | - | 4 | - | - | 1460 | 0 | 128 | 260 | 3077 | no | hero scrolled out |
| 2 | B | 5.85 | cap | - | 3693 | - | 12 | - | - | 1545 | 0 | 1521 | 602 | 3628 | no | skeleton hero |
| 3 | C | 5.67 | cap | - | 3811 | - | 12 | - | - | 1504 | 0 | 1876 | 841 | 3216 | no | skeleton hero |
| 4 | A | 5.75 | cap | - | 2757 | - | 4 | - | - | 1461 | 0 | 321 | -29 | 3285 | no | hero scrolled out |
| 5 | B | 6.11 | cap | - | 3707 | - | 12 | - | - | 1400 | 0 | 1726 | 552 | 3428 | no | skeleton hero |
| 6 | C | 5.48 | cap | - | 3477 | - | 4 | - | - | 1462 | 0 | 1665 | 1003 | 3247 | no | skeleton hero |
| 7 | A | 5.52 | cap | - | 2814 | - | 4 | - | - | 1767 | 0 | -505 | -343 | 3956 | no | hero scrolled out |
| 8 | B | 5.59 | cap | - | 4099 | - | 17 | - | - | 1445 | 0 | 1018 | 463 | 3199 | no | skeleton hero |
| 9 | C | 5.2 | cap | - | 2770 | - | 4 | - | - | 1452 | 0 | 1393 | 1558 | 2934 | no | hero scrolled out |
| 10 | A | 5.21 | cap | - | 3349 | - | 8 | - | - | 1479 | 0 | -443 | -2332 | 3174 | no | skeleton hero |
| 11 | B | 5.04 | cap | - | 4260 | - | 17 | - | - | 1427 | 0 | 1375 | 821 | 3019 | yes | backdrop |
| 12 | C | 5.03 | cap | - | 3412 | - | 8 | - | - | 1500 | 0 | 2523 | 1092 | 3684 | no | skeleton hero |
| 13 | A | 5.35 | cap | - | 3321 | - | 8 | - | - | 1465 | 0 | -902 | -2759 | 3711 | no | skeleton hero |
| 14 | B | 5.44 | cap | - | 2764 | - | 4 | - | - | 1528 | 0 | 1024 | 1112 | 2668 | no | hero scrolled out |
| 15 | C | 5.89 | cap | - | 2736 | - | 4 | - | - | 1530 | 0 | 1671 | 1815 | 3768 | no | hero scrolled out |
| 16 | A | 5.89 | cap | - | 3162 | - | 8 | - | - | 1450 | 0 | -821 | -2720 | 3657 | no | skeleton hero |
| 17 | B | 5.88 | cap | - | 3849 | - | 13 | - | - | 1475 | 0 | 1207 | 682 | 3230 | no | skeleton hero |
| 18 | C | 5.74 | cap | - | 3535 | - | 13 | - | - | 1457 | 0 | 1783 | 1130 | 2929 | no | skeleton hero |
| 19 | A | 5.52 | cap | - | 2770 | - | 4 | - | - | 1477 | 0 | 241 | 344 | 3096 | no | hero scrolled out |
| 20 | B | 5.48 | cap | - | 2744 | - | 4 | - | - | 1571 | 0 | 1075 | 1152 | 2735 | no | hero scrolled out |
| 21 | C | 5.17 | cap | - | 2751 | - | 4 | - | - | 1408 | 0 | 2340 | 1888 | 4199 | no | hero scrolled out |
| 22 | A | 5.1 | cap | - | 2746 | - | 4 | - | - | 1484 | 0 | 312 | 428 | 3199 | no | hero scrolled out |
| 23 | B | 5.15 | cap | - | 2725 | - | 4 | - | - | 1515 | 0 | 1377 | 556 | 3574 | no | hero scrolled out |
| 24 | C | 4.91 | cap | - | 3696 | - | 12 | - | - | 1447 | 0 | 1770 | 885 | 3271 | no | skeleton hero |
| 25 | A | 5.67 | cap | - | 2719 | - | 4 | - | - | 1513 | 0 | -318 | -219 | 50 (no line) | no | hero scrolled out |
| 26 | B | 5.45 | cap | - | 3692 | - | 12 | - | - | 1507 | 0 | 1738 | 736 | 3427 | no | skeleton hero |
| 27 | C | 5.07 | cap | - | 3811 | - | 12 | - | - | 1486 | 0 | 2608 | 1422 | 3140 | no | skeleton hero |
| 28 | A | 5 | cap | - | 3606 | - | 12 | - | - | 1594 | 0 | -1113 | -1672 | 50 (no line) | no | hero scrolled out |
| 29 | B | 5.01 | cap | - | 2742 | - | 4 | - | - | 1479 | 0 | 1039 | 1156 | 2320 | no | hero scrolled out |
| 30 | C | 5.28 | cap | - | 3682 | - | 12 | - | - | 1497 | 0 | 2362 | 894 | 2739 | no | skeleton hero |
| 31 | A | 5.62 | cap | - | 3754 | - | 12 | - | - | 1502 | 0 | -1302 | -2423 | 1793 | no | skeleton hero |
| 32 | B | 5.17 | cap | - | 2742 | - | 4 | - | - | 1464 | 0 | 1554 | 1679 | 2909 | no | hero scrolled out |
| 33 | C | 5.55 | cap | - | 3727 | - | 12 | - | - | 1491 | 0 | 1853 | 892 | 4022 | no | skeleton hero |
| 34 | A | 5.47 | cap | - | 2741 | - | 4 | - | - | 1433 | 0 | 442 | 571 | 3284 | no | hero scrolled out |
| 35 | B | 5.6 | cap | - | 2766 | - | 4 | - | - | 1545 | 0 | 977 | 1126 | 3221 | no | hero scrolled out |
| 36 | C | 5.18 | cap | - | 3670 | - | 12 | - | - | 1456 | 0 | 1970 | 838 | 3563 | no | skeleton hero |

## Summary

First-key `key_lat` per anchor (ms; p95 is nearest rank, equal to the maximum for 12 starts):

| anchor | n | median | p75 | p95 | max | press-R min / median / max | press-visible min / median / max |
|---|---|---|---|---|---|---|---|
| A | 12 | 3186.5 | 3285 | 3956 | 3956 | -1302 / -380.5 / 442 | -2759 / -281 / 571 |
| B | 12 | 3210 | 3427 | 3628 | 3628 | 977 / 1291 / 1738 | 463 / 778.5 / 1679 |
| C | 12 | 3259 | 3684 | 4199 | 4199 | 1393 / 1864.5 / 2608 | 838 / 1047.5 / 1888 |
| all | 36 | 3218.5 | 3563 | 4022 | 4199 | -1302 / 1291 / 2608 | -2759 / 778.5 / 1888 |

| Metric | Value |
|---|---|
| Starts with `cause: cap` | 36 of 36 |
| Starts with a `ready` mark in the line | 0 of 36 (the line is written once `first_content` and `revealed` exist, which is the cap reveal, before `ready` takes effect; `img_idle`, `ready_in_flight`, `ready_drained` are absent for the same reason) |
| `revealed` ms, min / median / max | 2719 / 3335 / 4260 (two groups: 2719-2814 and 3162-4260) |
| `first_content` ms, min / median / max | 1400 / 1479 / 1767 |
| `in_flight` at reveal | 4 (17 starts), 8 (4), 12 (11), 13 (2), 17 (2) |
| `dropped_keys` | 0 in all 36 (no key reached the app under the splash) |
| Hero backdrop drawn at fade end | 1 of 36 (r11B, start 11); 18 skeleton hero on screen; 17 hero scrolled out of view, 16 of those are the starts with `revealed` < 3000 |
| Launch to splash gone, from the recording, ms, min / median / max | 2952 / 4412.5 / 5489 |
| Main-thread frames (3 atrace starts, first 4.5 s after bindApplication) | 38-49 `doFrame`; 11-15 over 50 ms, 6-8 over 200 ms, longest 0.51-0.84 s; JIT thread pool busy 5.3-5.7 s of the trace |
| atrace starts (key at launch +5 s), `revealed` / key_lat | 3084 / 3893, 4187 / 2754, 3826 / 3416 |

AC-5 at baseline: every start is cap-revealed with 4-17 requests in flight; no start has a `ready` mark, so "starts with ready < 2500" is empty and `d = ready_drained - ready` cannot be computed (missing = +inf by the Task 7 rule). AC-6 at baseline: first-key p95 is 3628 ms (B) and 4199 ms (C) against 199 ms. AC-8 baseline hero count: 1 of 36.

## 8428c946 reference (launch to visible content, one set of 10 starts, no key)

Device and method as above; 8428c946 has no reveal gate: its window background (wordmark) shows, then Home. Its dev build is 1.12.3 and the box then offers the 1.12.5 update, so an update dialog shows over Home in every reference start. Metric: seconds from the first launch-transition frame to the first frame with the rail visible and no wordmark (rail dimmed by the dialog); `first_content` ms since process start from its `startup` line (no `revealed` there).

| start | launch to rail visible, s | first_content ms |
|---|---|---|
| 1 | 2.00 | 3131 |
| 2 | 2.00 | 2855 |
| 3 | 2.12 | 2887 |
| 4 | 1.98 | 2992 |
| 5 | 2.00 | 2954 |
| 6 | 2.25 | 2904 |
| 7 | 2.05 | 3028 |
| 8 | 1.97 | 2948 |
| 9 | 2.30 | 2838 |
| 10 | 2.02 | 3118 |

Median launch to rail visible 2.01 s (3.20-3.55 s absolute). Phase-A over the same metric (launch to the splash gone, 36 starts): median 4.41 s, 2.95-5.49 s.

## Limits

- B and C are not at +300 / +1000 ms of the reveal: injection by `input` costs 0.53-0.59 s on an idle box and 0.78-3.44 s (median 1.44 s) from the injection stamp to the app's key mark in these starts, and the real-time poll lags during the same jank. Realised press-visible: B 463-1679 ms (median 779), C 838-1888 ms (median 1048), A -2759 to 571 ms. Latency does not depend on the anchor in this range (medians 3186-3259 ms), so the AC-6 baseline stands; the re-measure uses this same method.
- The visible reveal start is read from a recording that skips frames while the main thread janks.
- The 8428c946 reference carries an update dialog and a different startup path; it is a launch-to-content reference only.
- atrace summaries are counts over main-thread slices (no trace stored).
