# Device evidence: startup-reveal-gate-on-slow-tv (verify round 1, stopped before the S1 set)

Date: 2026-11-23. Device: TV box 192.168.1.110:5555 (KM6, Android 10, API 29), package `com.kino.puber.stage` only. Run head 7ed2f1ec, base 9158cf82.

## Session precondition
- Stage logged in before any install (Home with content on the installed build).
- Master devDebug build of 9158cf82 installed with install-preserve (outcome `installed`, `compatible_replace`, `destructive_action` false), launched, Home reached; `make DEVICE=192.168.1.110:5555 auth-save` saved 13772841 bytes to `.auth/` (gitignored).

## Build and install
- Worktree `devBenchmarkRelease` of 7ed2f1ec: apk sha256 `c31dab2d965ec1032d908beb2e76ac27ff85f8679f4753e36d469ca12aad293c`. On-device `base.apk` sha256 equal.
- install-preserve (run twice, same APK, the second to capture the JSON): outcome `installed`, `compatible_replace`, `destructive_action` false, same signer. No uninstall, data clear or downgrade. The app was not launched after the install, so the box keeps the recipe state of release-startup-compilation round 4.

## Compile state (read-only)
- `dumpsys package com.kino.puber.stage`: `arm: [status=speed-profile] [reason=install]`.
- `cmd package dump-profiles`: profile `ProfileInfo: empty` and reference profile `ProfileInfo: empty`.
- This is S0 (the same dumpsys line as the S0 of release-startup-compilation, where `status=speed-profile` appears with `reason=install` and empty profiles), not S1. S1 needs a compile or a background dexopt (`reason=bg-dexopt` or `unknown` with a non-empty reference profile). No forced compile was run (not authorised for qa).

## ACs
| AC | Result |
|---|---|
| AC-6, AC-7, AC-8, AC-10 | NOT CHECKED: S1 not reached |
| AC-9 | NOT CHECKED here: the box is in S0 now, but launching the app would add runtime profile data and change the recipe state the conductor needs for S1 (round 4: app not launched after the install); the test design runs S0 after S1 |


# Round 1b: S1 and S0 sets (same APK c31dab2d, head 7ed2f1ec)

Date: 2026-11-23. Device: TV box 192.168.1.110:5555 (KM6, Android 10), package `com.kino.puber.stage` only, leased and released. Emulator not used.

## State before the sets
- Conductor (owner-authorised) took the box to S1: dump-profiles both empty, INSTALL_PROFILE result=1, force-stop, `compile -m speed-profile -f` Success. Read-only check by qa before any start: `arm: [status=speed-profile] [reason=unknown]`; on-device `base.apk` sha256 `c31dab2d965ec1032d908beb2e76ac27ff85f8679f4753e36d469ca12aad293c`, equal to the host APK; app not running. qa ran no compile and no reinstall for the S1 sets. After all S1 sets the dumpsys line was unchanged.
- Telemetry session log was ON (one file per start; fields as in release-startup-compilation). No logcat used.

## Method
- Scaffolding: an on-device `app_process` helper (Java, test scaffolding, kept as `inj/Inj.java` in the qa scratchpad, not in Git; in /data/local/tmp during the run, removed afterwards). It does the whole cold start: `am force-stop`, 2.5 s, `am start`, 17 s per start; injects KEYCODE_DPAD_DOWN through `InputManager.injectInputEvent` (DOWN then UP, source KEYBOARD). Times are the box wall clock; process start = session `wall_ms` minus its `t`, so "inj - revealed" is on the telemetry clock.
- Anchors (as verify-5): A launch +3.0 s; B first non-black pixel at (20,72) by a pair of staggered `screencap` loops (150 ms apart; a sample needs black seen before a non-black one counts); C detection +0.7 s. Second key K2 (AC-10) at detection +2.5 s in every start. 5 starts per anchor, interleaved A, B, C.
- Control with the baseline method (release-startup-compilation round 4): `am start`, `input keyevent KEYCODE_DPAD_DOWN` at launch +2.44 s, no screencap, 17 s per start. First key reaches the app at launch +2.8 s to +3.0 s.
- One uncounted warm-up start (first launch after the compile: cause cap, ready 3150, revealed 2846) precedes the sets.
- The detector sets use more load than the baseline method: three sets are reported. **Set 1** started the detector at launch +0.8 s. It delayed the content (ready 1.9-2.5 s, 6 of 15 starts reveal by cap) and is kept as a disturbed set, not used for AC-6. **Set 2** started the detector at launch +1.9 s, as verify-5 (detector from +2.0 s) did. **Control** uses the baseline method.
- Baseline for AC-6 (reused, no new run): S1 rounds 3-4 of release-startup-compilation, 45 starts: `revealed - ready` median 473, max 642, 3 of 45 cap starts with ready < 2500, first key over 199 ms in 2 of 45.

## Set 2: anchored S1 set (15 counted)
Box load before the starts 3.43-4.10. `-` = absent. A `key_lat` line is written only above 50 ms; no line = at most 50 ms. In every start the one line belongs to the first key (checked by the `t` order against the `key` lines); no K2 has a `key_lat` line.

| # | anchor | load | cause | first_content | ready_req | ready | ready_in_flight | ready_drained | revealed | revealed - ready | inj after launch (key1 / K2) | inj - revealed (key1 / K2) | detect - revealed | key_lat ms / wait (first key) | dropped | prof |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | A | 3.79 | grace | 913 | 1740 | 1773 | 4 | 2099 | 2099 | 326 | 3000 / 4995 | 672 / 2667 | 167 | no line | 0 | 1 |
| 2 | B | 3.65 | grace | 777 | 1963 | 2001 | 12 | 2808 | 2352 | 351 | 2694 / 5194 | 126 / 2626 | 126 | 399/w15 | 0 | 1 |
| 3 | C | 3.83 | grace | 858 | 1842 | 1880 | 12 | 2737 | 2217 | 337 | 3409 / 5209 | 970 / 2770 | 270 | no line | 0 | 1 |
| 4 | A | 4.10 | ready | 824 | 1719 | 1781 | 3 | 1983 | 1995 | 214 | 3000 / 4838 | 765 / 2603 | 103 | no line | 0 | 1 |
| 5 | B | 3.63 | grace | 813 | 1663 | 1821 | 8 | 2519 | 2150 | 329 | 2473 / 4971 | 97 / 2595 | 95 | 304/w20 | 0 | 1 |
| 6 | C | 3.43 | grace | 824 | 1706 | 1876 | 8 | 2601 | 2255 | 379 | 3337 / 5137 | 852 / 2652 | 152 | 58/w23 | 0 | 1 |
| 7 | A | 3.72 | grace | 780 | 1487 | 1662 | 8 | 2183 | 1985 | 323 | 3000 / 4865 | 782 / 2647 | 147 | no line | 0 | 1 |
| 8 | B | 3.63 | grace | 831 | 1775 | 1832 | 6 | 2504 | 2154 | 322 | 2523 / 5022 | 136 / 2635 | 135 | 291/w22 | 0 | 1 |
| 9 | C | 3.45 | grace | 803 | 1674 | 1834 | 8 | 2677 | 2148 | 314 | 3380 / 5180 | 1016 / 2816 | 316 | no line | 0 | 1 |
| 10 | A | 4.03 | grace | 886 | 1864 | 1901 | 6 | 2614 | 2232 | 331 | 3000 / 5230 | 523 / 2753 | 253 | no line | 0 | 1 |
| 11 | B | 3.99 | grace | 825 | 1574 | 1732 | 7 | 2358 | 2062 | 330 | 2475 / 4973 | 170 / 2668 | 168 | 356/w11 | 0 | 1 |
| 12 | C | 3.76 | grace | 835 | 1685 | 1867 | 7 | 2523 | 2177 | 310 | 3237 / 5037 | 850 / 2650 | 150 | 59/w11 | 0 | 1 |
| 13 | A | 3.81 | grace | 874 | 1709 | 1814 | 4 | 2148 | 2130 | 316 | 3000 / 4985 | 647 / 2632 | 132 | no line | 0 | 1 |
| 14 | B | 3.82 | grace | 882 | 1923 | 1998 | 12 | 2862 | 2360 | 362 | 2818 / 5318 | 232 / 2732 | 232 | 543/w26 | 0 | 1 |
| 15 | C | 3.95 | grace | 803 | 1631 | 1685 | 5 | 2188 | 2003 | 318 | 3111 / 4911 | 863 / 2663 | 163 | no line | 0 | 1 |

Summary: cause grace 14, ready 1, cap 0. `revealed - ready` median 326, min 214, max 379 (the one `ready` cause start is 214). ready median 1832, revealed median 2150. First key above 199 ms: anchor A 0 of 5 (all no line), anchor B 5 of 5 (399, 304, 291, 356, 543 ms; wait_ms 11-26, frame_ms 19-54), anchor C 0 of 5 (no line x3, 58, 59). Total 5 of 15. Injection came 97-232 ms after the reveal mark at B, 523-782 ms at A, 852-1016 ms at C. K2 (detection +2.5 s, 2595-2816 ms after the mark): 0 of 15 above 50 ms.

## Control: baseline method, S1 (15 counted after 1 uncounted)
Load 3.31-4.17.

| # | load | cause | first_content | ready_req | ready | revealed | revealed - ready | key_lat | dropped | prof |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 3.32 | grace | 898 | 1827 | 1866 | 2181 | 315 | no line | 0 | 1 |
| 2 | 3.92 | grace | 856 | 1642 | 1796 | 2103 | 307 | no line | 0 | 1 |
| 3 | 3.74 | grace | 788 | 1773 | 1809 | 2129 | 320 | no line | 0 | 1 |
| 4 | 3.95 | grace | 829 | 1642 | 1869 | 2200 | 331 | no line | 0 | 1 |
| 5 | 4.06 | grace | 818 | 1602 | 1754 | 2069 | 315 | no line | 0 | 1 |
| 6 | 3.82 | grace | 816 | 1667 | 1841 | 2164 | 323 | no line | 0 | 1 |
| 7 | 3.31 | grace | 819 | 1704 | 1746 | 2074 | 328 | no line | 0 | 1 |
| 8 | 3.76 | ready | 903 | 1585 | 1747 | 2060 | 313 | no line | 0 | 1 |
| 9 | 3.82 | grace | 881 | 1561 | 1718 | 2050 | 332 | no line | 0 | 1 |
| 10 | 4.17 | grace | 831 | 1773 | 1805 | 2133 | 328 | no line | 0 | 1 |
| 11 | 3.63 | grace | 817 | 1701 | 1848 | 2179 | 331 | no line | 0 | 1 |
| 12 | 3.79 | grace | 857 | 1723 | 1804 | 2131 | 327 | no line | 0 | 1 |
| 13 | 4.11 | grace | 825 | 1648 | 1890 | 2211 | 321 | no line | 0 | 1 |
| 14 | 3.86 | grace | 832 | 1750 | 1911 | 2232 | 321 | no line | 0 | 1 |
| 15 | 3.81 | grace | 842 | 1688 | 1835 | 2151 | 316 | no line | 0 | 1 |

Summary: cause grace 14, ready 1, cap 0. `revealed - ready` median 321, min 307, max 332. ready median 1809, revealed median 2133. First key (launch +2.44 s, arrives about +2.8 s, 600-1000 ms after the reveal): no `key_lat` line in 15 of 15, so every key took 50 ms or less; dropped_keys 0; `prof` 1; one `startup` line per start.

## Set 1: detector from +0.8 s (disturbed, 15 counted)
Load 3.5-4.44.

| # | anchor | load | cause | first_content | ready_req | ready | ready_in_flight | ready_drained | revealed | revealed - ready | inj after launch (key1 / K2) | inj - revealed (key1 / K2) | detect - revealed | key_lat ms / wait (first key) | dropped | prof |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | A | 3.50 | grace | 951 | 2146 | 2286 | 8 | 3027 | 2609 | 323 | 3000 / 5422 | 161 / 2583 | 83 | 329/w7 | 0 | 1 |
| 2 | B | 3.66 | grace | 855 | 2155 | 2227 | 6 | 2849 | 2564 | 337 | 3021 / 5520 | 239 / 2738 | 238 | 538/w6 | 0 | 1 |
| 3 | C | 4.27 | cap | 920 | 2220 | 2458 | 8 | 3304 | 2654 | 196 | 4019 / 5819 | 1143 / 2943 | 443 | no line | 0 | 1 |
| 4 | A | 4.07 | cap | 830 | 1890 | 2400 | 8 | 3211 | 2624 | 224 | 3000 / 5576 | 153 / 2729 | 229 | 657/w38 | 0 | 1 |
| 5 | B | 3.98 | grace | 912 | 2261 | 2307 | 7 | 2896 | 2634 | 327 | 3105 / 5605 | 241 / 2741 | 241 | 286/w14 | 0 | 1 |
| 6 | C | 4.39 | cap | 874 | 2266 | 2323 | 4 | 2780 | 2646 | 323 | 3692 / 5492 | 820 / 2620 | 120 | no line | 0 | 1 |
| 7 | A | 4.44 | grace | 908 | 1810 | 1890 | 7 | 2521 | 2205 | 315 | 3000 / 5169 | 568 / 2737 | 237 | 102/w4 | 0 | 1 |
| 8 | B | 3.90 | grace | 905 | 2174 | 2283 | 5 | 2885 | 2626 | 343 | 3037 / 5536 | 173 / 2672 | 172 | 292/w12 | 0 | 1 |
| 9 | C | 3.84 | grace | 877 | 1968 | 2091 | 5 | 2677 | 2438 | 347 | 3473 / 5273 | 811 / 2611 | 111 | no line | 0 | 1 |
| 10 | A | 4.03 | cap | 840 | 2068 | 2431 | 8 | 3187 | 2645 | 214 | 3000 / 5667 | 130 / 2797 | 297 | 655/w13 | 0 | 1 |
| 11 | B | 4.30 | grace | 909 | 1968 | 2054 | 7 | 2697 | 2367 | 313 | 2786 / 5285 | 192 / 2691 | 191 | 457/w30 | 0 | 1 |
| 12 | C | 3.86 | cap | 885 | 2388 | 2470 | 4 | 2913 | 2638 | 168 | 3688 / 5488 | 832 / 2632 | 132 | no line | 0 | 1 |
| 13 | A | 4.13 | grace | 1018 | 1895 | 2175 | 6 | 2873 | 2500 | 325 | 3000 / 5422 | 270 / 2692 | 192 | 530/w26 | 0 | 1 |
| 14 | B | 4.31 | grace | 940 | 2105 | 2185 | 7 | 2859 | 2499 | 314 | 2871 / 5370 | 148 / 2647 | 147 | 363/w4 | 0 | 1 |
| 15 | C | 4.00 | cap | 951 | 2395 | 2467 | 4 | 2942 | 2647 | 180 | 3697 / 5497 | 817 / 2617 | 117 | no line | 0 | 1 |

Summary: cause grace 9, cap 6 (all six have ready < 2500: 2458, 2323, 2431, 2470, 2467, 2400; the cap at 2.5 s fired before ready + 300). `revealed - ready` median 315, max 347. ready median 2286 against 1832 in set 2 and 1809 in the control, so the early screencap load slowed the content. First key over 199 ms: A 4 of 5 (329, 657, 655, 530; 102), B 5 of 5 (538, 286, 292, 457, 363), C 0 of 5. Not used for the verdict.

## AC-8: screenshot after a grace-caused fade
Set 1 start 1 (cause grace), screenshot at detection +1.2 s (about 1.3 s after the reveal mark, after the key at A). Described: the Home screen is fully painted, no skeleton or placeholder block, no splash left; the hero image, the "Я смотрю" row with three full cards and a fourth cut at the right edge, and the "Мои закладки" row header with its card images; the first rail card has the focus ring. The shot shows the account's watch list, so it is not committed. A second screenshot at the end of a start with two keys (set 2 style, anchor A) shows the focus ring on the first card of the second rail: K1 moved hero to the first rail, K2 one row further, no jump of two rows. Whether skeletons would be visible during the 300 ms fade itself stays the owner's visual judgement (R9).

## AC-9: S0 (5 cold starts, baseline method)
- Reached by a same-APK `install-preserve` reinstall: outcome `installed`, classification `compatible_replace`, `destructive_action` false, same signer; on-device sha256 equal; no compile. `dumpsys`: `arm: [status=speed-profile] [reason=install]` before and after the starts; `cmd package dump-profiles` before: profile and reference profile both `ProfileInfo: empty`.

| # | load | cause | first_content | ready_req | ready | revealed | revealed - ready | key_lat / wait | dropped | prof |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 3.67 | cap | 1108 | 3426 | 4383 | 3425 | -958 | 2297/w112 | 0 | 2 |
| 2 | 3.53 | cap | 1142 | 4247 | 4497 | 3950 | -547 | 3239/w327 | 0 | 2 |
| 3 | 4.06 | cap | 1138 | 4463 | 5089 | 3958 | -1131 | 3258/w154 | 0 | 2 |
| 4 | 3.97 | cap | 1108 | 2531 | 3691 | 3588 | -103 | no line | 1 | 2 |
| 5 | 4.29 | cap | 1103 | 3202 | 3470 | 3201 | -269 | 2603/w313 | 0 | 2 |

Summary: cause cap 5 of 5 (no grace), revealed 3201-3958 ms, median 3588 (release-startup-compilation new S0 3381, base S0 3269; spread in those sets 2886-3748); ready after the reveal in all five, `prof` 2 (no baseline profile applied), one `startup` line per start. No crash, no ANR. Not clearly changed by this run: S0 reveals by cap as before.

## Result per AC
| AC | Result |
|---|---|
| AC-6 | PASS on set 2 and on the control: `revealed - ready` median 326 / 321 (limit 350), max 379 / 332 (limit 550), cap starts with ready < 2500: 0 of 15 / 0 of 15; cause grace 14 of 15 in both (the production `graceHook` fires). Limit of the bound: the cap counts from `begin()`, ready from process start, and begin is about 125-155 ms after process start. The bound holds only while ready - begin <= cap - G = 2500 - 300 = 2200 ms, which is about ready <= 2330 ms on the line's clock. Later than that the 2.5 s cap fires first and the reveal comes before ready + G. Set 1 shows the edge: rows 1, 5, 8 (ready 2286, 2307, 2283) are still grace, row 6 (ready 2323) is the first cap, and 6 of 15 starts revealed by cap with ready 2323-2470 ms (`revealed - ready` 168-323). In set 2 ready was 1662-2001 ms and in the control 1718-1911 ms, inside the limit, so they do not test beyond it. Set 1 (detector from +0.8 s) is a method effect, see above; reported to the owner as a known limit |
| AC-7 (restated, owner decision 2026-11-23, state.md) | Latency clause: PASS on the restated bound, at most one first key over 199 ms across anchors A and C and the baseline-method control. Set 2 anchor A 0 of 5 (no `key_lat` line, so at most 50 ms), anchor C 0 of 5 (no line x3, 58, 59 ms), control 0 of 15 (no line); total 0 of 25 over 199 ms, limit 1. Focus clause (ring moves one row per key, no jump of two rows): NOT CHECKED on the 25 counted starts. One screenshot from an uncounted or unidentified anchor A start with two keys shows one row per key; K2 0 of 15 above 50 ms in set 2 is a latency figure, not a focus check. Handed to the owner at gate 2. Anchor B is an observation, not part of the bound: 5 of 5 first keys over 199 ms (399, 304, 291, 356, 543 ms) against `ready_drained`: the key arrived 97-232 ms after the reveal mark and 126-330 ms before `ready_drained` (which is 296-502 ms after the mark), so it fell into the post-ready image burst; at A all five keys arrived after the drain (523-782 ms after the mark). Set 1 is not used (A 4 of 5 over 199 ms under detector load). The B latency goes to startup-post-reveal-key-latency |
| AC-8 | observation given (above); skeleton judgement left to the owner |
| AC-9 | PASS (observation): S0 starts unchanged, cap 5 of 5 |
| AC-10 | PASS (observation, latency): K2 at detection +2.5 s, 0 of 15 over 50 ms; focus per key is NOT CHECKED here, see the AC-7 focus clause |

Cleanup: `inj.jar`, `inj.txt` and screenshots removed from /data/local/tmp (only an unrelated `x` and `dalvik-cache` remain); lease released. The box is left in S0 (`reason=install`) with the c31dab2d APK; S1 is gone.
