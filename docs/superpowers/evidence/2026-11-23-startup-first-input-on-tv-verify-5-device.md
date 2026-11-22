# Device evidence: startup-first-input-on-tv verify round 5 (AC-6 compiled state, Manual check 4), HEAD e7515c84

Result: PASS (observation). The API answered; every start reached Home content.

## Device and build

- Device: TV box 192.168.1.110:5555 (KM6, Android 10), leased and released. Emulator not used. Package com.kino.puber.stage only.
- Build: the installed `devBenchmarkRelease` of tree 79afebd3. On-device base.apk sha256 efb00a3a18f27490d85ffa5bcd3c6caa3579edc592dc463f3d49a5adc1aa7ee9, equal to rounds 2-4, checked before and after. No reinstall, no compile by qa.
- Compiler filter (`dumpsys package com.kino.puber.stage`, Dexopt state) before the set and after the atrace starts: `arm: [status=speed-profile] [reason=unknown]` (the conductor's compile of 2026-11-22; the reason did not change overnight).
- Box load average (1 min): 2.70 before, 3.80 after the 15 starts, 3.60 at the end.
- Method as in rounds 2-4: on-device `app_process` injector (KEYCODE_DPAD_DOWN through InputManager.injectInputEvent, test scaffolding in /data/local/tmp, removed afterwards), anchors A (launch +3 s), B (first non-black pixel at (20,72), injected at detection), C (detection +0.7 s); cold start = force-stop, 2.5 s, am start, 17 s per start. A screenshot was taken at the end of every start (account data on screen; not committed).
- A 3-start probe (A, B, C) ran first: all Content. Its 3 starts are rows 1-3 below; 12 more followed (4 A, 4 B, 4 C interleaved).

## Per start

`-` = absent. inj - revealed is the injection minus the telemetry `revealed` mark on one clock. focus border = the screenshot at the end of the start shows the first card of the "Я смотрю" row outlined (see Key handled).

| # | anchor | cause | first_content | ready_req | ready | ready_in_flight | ready_drained | revealed | inj after launch | inj - revealed | detect - revealed | ready - revealed | ready_req - first_content | lat_ms | wait_ms | key line | dropped_keys | focus border |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 (p1) | A | ready | 821 | 1325 | 1380 | 11 | 2319 | 2336 | 3033 | 492 | - | -956 | 504 | no line | - | 1 key | 0 | yes |
| 2 (p2) | B | cap | 929 | 1602 | 1639 | 6 | 2866 | 2629 | 3012 | 189 | 117 | -990 | 673 | no line | - | 1 key | 0 | yes |
| 3 (p3) | C | ready | 803 | 1209 | 1404 | 7 | 1906 | 1915 | 3142 | 1038 | 255 | -511 | 406 | no line | - | 1 key | 0 | yes |
| 4 (s1) | A | ready | 830 | 1506 | 1552 | 10 | 2288 | 2303 | 3030 | 519 | - | -751 | 676 | no line | - | 1 key | 0 | yes |
| 5 (s2) | B | ready | 814 | 1472 | 1505 | 10 | 2388 | 2407 | 2906 | 287 | 240 | -902 | 658 | no line | - | 1 key | 0 | yes |
| 6 (s3) | C | ready | 879 | 1603 | 1652 | 6 | 2127 | 2144 | 3380 | 1046 | 270 | -492 | 724 | no line | - | 1 key | 0 | yes |
| 7 (s4) | A | ready | 839 | 1493 | 1564 | 8 | 2132 | 2151 | 3038 | 692 | - | -587 | 654 | no line | - | 1 key | 0 | yes |
| 8 (s5) | B | ready | 813 | 1248 | 1441 | 6 | 1818 | 1830 | 2372 | 345 | 287 | -389 | 435 | no line | - | 1 key | 0 | yes |
| 9 (s6) | C | ready | 822 | 1449 | 1517 | 8 | 2071 | 2086 | 3218 | 933 | 123 | -569 | 627 | no line | - | 1 key | 0 | yes |
| 10 (s7) | A | ready | 792 | 1256 | 1455 | 7 | 1936 | 1946 | 3026 | 884 | - | -491 | 464 | no line | - | 1 key | 0 | yes |
| 11 (s8) | B | ready | 834 | 1620 | 1685 | 12 | 2504 | 2513 | 2958 | 251 | 174 | -828 | 786 | no line | - | 1 key | 0 | yes |
| 12 (s9) | C | ready | 792 | 1390 | 1600 | 5 | 2040 | 2060 | 3172 | 919 | 118 | -460 | 598 | no line | - | 1 key | 0 | yes |
| 13 (s10) | A | ready | 840 | 1530 | 1638 | 5 | 2097 | 2105 | 3028 | 705 | - | -467 | 690 | no line | - | 1 key | 0 | yes |
| 14 (s11) | B | cap | 961 | 1673 | 1710 | 12 | 2717 | 2628 | 3005 | 183 | 112 | -918 | 712 | 67 | 8 | 1 key | 0 | yes |
| 15 (s12) | C | ready | 836 | 1604 | 1690 | 6 | 2491 | 2503 | 3633 | 931 | 136 | -813 | 768 | no line | - | 1 key | 0 | yes |

## Per anchor

| anchor | content starts | key_lat lines | lat_ms | wait_ms | injected after launch | inj - revealed | ready - revealed med / max | ready_req - first_content med / max |
|---|---|---|---|---|---|---|---|---|
| A | 5 | 0 | - | - | 3026-3038 ms | 492 / 692 / 884 ms | -587 / -467 | 654 / 690 |
| B | 5 | 1 | 67 | 8 | 2372-3012 ms | 183 / 251 / 345 ms | -902 / -389 | 673 / 786 |
| C | 5 | 0 | - | - | 3142-3633 ms | 919 / 933 / 1046 ms | -511 / -460 | 627 / 768 |

`lat_ms` / `wait_ms` exist on 1 of 15 starts (row 14: lat 67 ms, wait 8 ms, `0 <= wait_ms <= lat_ms` holds, not expired). The other 14 have a `key` line (n = 1) and no `key_lat` line. A `key_lat` line is only written above 50 ms, so every one of those keys was handled in 50 ms or less. Medians and p95 per anchor are therefore not computable from lines; the bound is "lat_ms <= 50 ms" for 14 of 15 starts and 67 ms for the other. Round 2 as installed (no compile): A lat 3654 / 3957 (median / p95), wait 592 / 1036; B lat 3666.5 / 4330, wait 329.5 / 515; C lat 4068.5 / 4745, wait 259 / 500.

## Key handled (not just unwritten)

An extra start with no key (cold start, 17 s, same screen) was screenshotted as the reference: no focus ring on the first card of "Я смотрю". Pixel brightness at the left border of that card (x 149-154, y 700-800): reference mean 62 (card art), all 15 keyed starts a uniform 148 (the ring). All 15 keyed starts show the ring on the first rail card, and none a second row down: DPAD_DOWN moved focus from the hero to the first rail card once, no jump of two rows. `dropped_keys` 0 in all 15.

## Startup lines

One `startup` line per start in all 15 (and in the 3 atrace starts), each with `cause`, `first_content`, `ready_req`, `ready`, `ready_in_flight`, `ready_drained`, `revealed`. Cause `ready` in 13, `cap` in 2 (rows 2 and 14). `ready - revealed` is negative in 13 of 15 starts (ready before the reveal). Compiled state vs round 2 (as installed, all `cap`): first_content median 830 ms (round 2 logged no first_content; its ready_req was 3.5-5.2 s), revealed median 2151 ms (round 2 2.7-4.7 s).

## Atrace, 3 starts (key injected at launch +3.0 s through the injector)

Main thread slices over 100 ms, offsets from bindApplication, atrace categories gfx view am wm input sched dalvik res for the package.

| start | key after launch | slices over 100 ms (offset, duration, name) |
|---|---|---|
| 1 | 3077 ms | 145 130 Choreographer#doFrame (+traversal); 942 175 Choreographer#doFrame (traversal 167, draw 167, Record View#draw 145, AndroidOwner:measureAndLayout 116); 1118 142 AndroidOwner:measureAndLayout; 1291 128 doFrame; 1435 123 doFrame |
| 2 | 3049 ms | 156 123 doFrame; 971 264 doFrame (traversal, draw 260, Record View#draw 230, measureAndLayout 167); 1382 123 doFrame (draw 104); 1534 117 doFrame |
| 3 | 3042 ms | 146 136 doFrame; 992 170 doFrame (draw 162, Record View#draw 145, measureAndLayout 109); 1162 152 AndroidOwner:measureAndLayout |

All slices are before 1.6 s after bindApplication; the key arrives about 2.8 s after it and nothing over 100 ms follows. Telemetry of the 3 starts: cause ready, `key` line, no `key_lat`, dropped_keys 0.

## AC-3 anchors, as realised

| anchor | target | realised | met |
|---|---|---|---|
| A | launch +3 s | 3026-3038 ms after launch; 492-884 ms after the reveal mark | yes, n = 5 |
| B | reveal +300 ms | 183 / 251 / 345 ms after the mark (min / median / max, detector 112-287 ms) | yes, n = 5 |
| C | reveal +1000 ms | 919-1046 ms after the mark (median 933) | yes, n = 5 (within about 80 ms of +1000) |

## Not done

- lat_ms / wait_ms median and p95 per anchor: not computable, 14 of 15 starts wrote no `key_lat` line (key handled in 50 ms or less, shown by the focus ring).
