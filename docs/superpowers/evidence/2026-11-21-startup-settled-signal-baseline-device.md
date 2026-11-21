# Device evidence: startup-settled-signal baseline (perf), base 7bcbfd83

Same content as /mnt/data/projects/PetProjets/Puber/.flow/runs/startup-settled-signal/baseline.md. Counter patch: 2026-11-21-startup-settled-signal-measure.patch (same directory).

## Device and build

- Device: emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU, logged in), leased (token held until the end) and released. The emulator process was killed once mid-run (host load rose to 12; ADB went offline); I restarted it with `-no-window -no-audio -no-boot-anim -no-snapshot-save -read-only -gpu swiftshader_indirect`. The app, login, telemetry setting and installed APK were intact after the restart (on-device base.apk sha256 re-checked). The first, interrupted attempt is not counted.
- Build: `devBenchmarkRelease` (R8, debug-signed) built in the worktree at base 7bcbfd83 with ONE measurement-only line added to HomeVM.publishSections, immediately before `updateViewState(HomeViewState.Content(...))`: `android.util.Log.i("SettledMeasure", "homePublish")`. Base has no publish counter (the telemetry `ready` event fires only on a state-class change), so this is the only way to count publishes. The patch is saved as `docs/superpowers/evidence/2026-11-21-startup-settled-signal-measure.patch`; the source was reverted after the build (`git status` clean apart from the evidence files). The "after" build must carry the same line for the publish count to be comparable (it is not part of the shipped change).
- APK sha256 8a584572...3866, versionCode 2090000000, 1.12.3-dev. Installed with install-preserve: outcome installed, compatible_replace, destructive_action false, installed signer = candidate signer 6b29181257cb...a83e, on-device base.apk sha256 equal. Nothing uninstalled, cleared or downgraded. Left installed.
- Telemetry was already enabled on the device (session files were written).

## Method

Cold start = `am force-stop`, 3 s, `am start -W`. Before each start the script waits (up to 2 min) for the 1-minute load average <= 9, then records it. 9 cold starts, the first dropped, 8 reported.
Marks (ms since process start) are the telemetry `startup` line: `app_composed`, `first_content`, `revealed`. Home ready = the Home `ready` Content `ms` (since Home start). Home publishes = count of `SettledMeasure homePublish` logcat lines for the whole start (about 14 s, until the telemetry file was read).
Keys: 14 DPAD keys (22, 21 alternating) sent from the device-side script about every 0.5 s starting right after `am start -W` returns (about 0.8 s after launch), until about 8 s. `key_lat` events are paired to the `key` event (delivery time, ms since process start) of the same key; a key without a `key_lat` counts as 50 ms (the app logs only above 50 ms). The measured set is the keys delivered in the first 3 s after `revealed` ([revealed, revealed + 3000]). Keys delivered before the reveal are not counted. Frames: `dumpsys gfxinfo reset` before the first key, dump 1 s after the last (about 7.5 s window).
Cap-revealed start: no marker exists at base. A start is classed as cap-revealed when `revealed` tracks the 2.5 s cap (`REVEAL_CAP_MS`, counted from `begin()`, which is about app_composed or earlier) and not Home's readiness: `revealed - 2500` is 191-593 ms for all 9 starts (about when Home composes), `LaunchFocusSettled` and the Home `ready` Content came 1.0-1.6 s after process start, i.e. about 1.3-1.5 s before `revealed`, and `revealed` does not move with `first_content`. So the reveal waited on something other than the hero-settled publish (the parent run's image-aware hold) and ended at the cap. It is an inference, not a mark.

## Raw runs

Columns: run, load, TotalTime ms, app_composed, first_content, revealed, Home ready ms, Home publishes, keys in the first 3 s after revealed, their key_lat ms, frames, janky, frame time p50/p90/p95/p99 ms.

| Run | Load | TotalTime | app_composed | first_content | revealed | Home ready | publishes | keys | key_lat (ms) | frames | janky | p50/p90/p95/p99 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 (dropped) | 4.62 | 651 | 509 | 1091 | 2691 | 134 | 8 | 4 | 886, 1046, 50, 50 | 143 | 132 (92.31%) | 113/150/250/1100 |
| 2 | 5.28 | 713 | 575 | 1124 | 2887 | 396 | 8 | 5 | 423, 254, 344, 154, 147 | 149 | 127 (85.23%) | 85/150/200/1200 |
| 3 | 6.87 | 717 | 610 | 1206 | 2849 | 375 | 8 | 5 | 765, 896, 1029, 1060, 1132 | 159 | 147 (92.45%) | 109/150/200/1050 |
| 4 | 6.95 | 708 | 569 | 1242 | 3030 | 487 | 8 | 5 | 305, 467, 50, 132, 50 | 148 | 135 (91.22%) | 81/150/250/1150 |
| 5 | 6.99 | 719 | 567 | 1072 | 2866 | 354 | 9 | 5 | 320, 199, 153, 50, 130 | 133 | 121 (90.98%) | 85/150/300/1250 |
| 6 | 8.13 | 676 | 553 | 1123 | 2779 | 406 | 8 | 5 | 496, 114, 225, 115, 50 | 146 | 134 (91.78%) | 85/150/150/1100 |
| 7 | 7.65 | 681 | 574 | 1299 | 3093 | 358 | 8 | 4 | 750, 881, 996, 1030 | 151 | 142 (94.04%) | 109/150/200/1050 |
| 8 | 6.55 | 763 | 591 | 1014 | 2822 | 202 | 8 | 5 | 673, 322, 399, 111, 106 | 144 | 126 (87.50%) | 85/150/200/1200 |
| 9 | 6.28 | 616 | 530 | 1085 | 2709 | 372 | 8 | 5 | 642, 913, 1000, 1060, 50 | 148 | 133 (89.86%) | 105/150/250/950 |

## Summary (runs 2-9, n = 8)

| Metric | Median (range) |
|---|---|
| **Home publishes per start** | **8** (8-9; 7 starts of 8, one of 9) |
| **`revealed` ms** | **2857.5** (2709-3093) |
| `first_content` ms | 1123.5 (1014-1299) |
| `app_composed` ms | 571.5 (530-610) |
| `am start -W` TotalTime ms | 710.5 (616-763) |
| Home ready ms (since Home start) | 373.5 (202-487) |
| **Starts revealed by the cap (inferred)** | **8 of 8** (9 of 9 with the dropped one) |
| Keys in the first 3 s after `revealed` | 39 pooled (4-5 per start); 34 of 39 above 50 ms |
| **`key_lat` p95, pooled over those 39 keys (unlogged = 50) ms** | **1060** (median 322, max 1132) |
| `key_lat` p95 per start | 423, 1132, 467, 320, 496, 1030, 673, 1060 |
| Frames in the ~7.5 s window | 148 (133-159) |
| Host load average during runs | 5.28-8.13 |

## Targets for the after measurement (brief AC-11, Q3)

- Home publishes per start below 8 (median), counted with the same one-line patch.
- `revealed` median not above 2857.5 + noise: use the spread, 2709-3093 (about 12 percent of the median, about 380 ms), as noise.
- No start revealed by the cap that was not before: at base every start is, so the check is vacuous unless the after build moves `revealed` off the cap.
- `key_lat` p95 over keys in the first 3 s after `revealed` is reported, not gated (emulator noise); base 1060 ms.

## Caveats

- Emulator with software GPU; host load 5.3-8.1 (lower than the parent run's, whose release sets ran at 9.5-15.6). The numbers are not comparable with the parent run's baseline.md: it used `first_content` as the anchor (no `revealed` existed) and keys after the telemetry line showed in the session file.
- Every start is revealed at the cap: Home is ready 1.0-1.6 s in and the splash still stays about 1.3-1.5 s more. That is why `revealed` hardly varies and why the keys in the window are slow (the first keys after the cap wait behind the decode and shader burst the parent run described). This finding matters for AC-11: a `revealed` that "does not regress" is already at the cap at base. Cause of the cap hold is not analysed here (it is outside this run's scope; see the parent's qa-report, Round 1c).
- The publish count includes publishes after the reveal and until about 14 s; 8 per start is consistent (one start 9), so the count is stable enough to compare.
- Janky percent is a gross check only (software GPU, nearly all frames janky).

## Device-side key script (/data/local/tmp/keys.sh)

```
#!/system/bin/sh
# keys at 1.0..7.5 s after launch begin, 500 ms spacing; prints send wall ms
P=com.kino.puber.stage
dumpsys gfxinfo $P reset >/dev/null
i=0
for k in 22 21 22 21 22 21 22 21 22 21 22 21 22 21; do
  echo "SEND $(date +%s%3N) $k"
  input keyevent $k
  sleep 0.4
done
sleep 1
dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th"
```
