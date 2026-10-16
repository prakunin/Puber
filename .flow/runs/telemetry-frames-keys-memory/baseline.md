# Baseline: telemetry-frames-keys-memory (perf), unchanged base cd16eb47

Device: emulator-5554 (AVD puber_tv_36, API 36 TV, 1920x1080, software GPU, logged in). Package com.kino.puber.stage.
Build: worktree's own devDebug APK built with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug`,
versionName 1.10.0-dev, sha256 753bfd91...c8b (on-device base.apk sha256 identical). Installed with
install-preserve: outcome `installed`, `compatible_replace`, destructive_action false. Lease taken and released.
"Log ON" = Settings > For developers > Session log toggled through the D-pad (confirmed on screen "Recording",
`telemetry_preferences.xml` holds `enabled_at_wall_ms`; session files appear in
`/sdcard/Android/data/<pkg>/files/telemetry/`, not in internal `files/`). Log left OFF at the end (prefs `<map />`).
Host load is the dominant noise: the emulator shares the host with other agents, so load average is quoted per block.

## Cold start (am start -W TotalTime ms; force-stop, 2 s, start, 6 s gap; 12 runs per block)
Debug build, so absolute values are high; time to first activity frame, not to Home content.

| Block | Log | Host load (1 min avg, start -> end) | Runs | Median | Min | Max |
|-------|-----|------|------|--------|-----|-----|
| A | OFF | 5 -> 26 (rising during block) | 1918 1581 1645 1563 1687 3136 2867 2697 3030 2877 2780 2826 | 2738 | 1563 | 3136 |
| B | ON  | 37 -> 34 | 3063 2781 2921 3146 2836 2904 2939 2914 3367 2827 2882 2801 | 2909 | 2781 | 3367 |
| C | OFF | 6 -> 6 (falling) | 1612 1560 1551 1590 1603 1556 1605 1558 1544 1507 1616 1544 | 1559 | 1507 | 1616 |
| D | ON  | 7 -> 3 | 1516 1591 1613 1590 1615 1542 1642 1575 1552 1596 1629 1605 | 1594 | 1516 | 1642 |

Fair A/B is C vs D (same low load, back to back, tight spread ~7%): OFF 1559 ms, ON 1594 ms, delta +35 ms (+2.2%),
inside the within-block spread (about 110 ms). A and B are not comparable (load 5 -> 26 and 35): the same build shows
a bimodal 1.5 s / 2.8 s depending on host load, so any later comparison must be against block C/D or be rerun back
to back at low load. Earlier S1 baseline (7 runs, load unknown): median 2804 ms, the high-load mode.

## Frames (dumpsys gfxinfo reset; Home; 6 Down, 8 Right, 8 Left, 6 Up with 0.35 s gaps; 3 runs per state)
Software GPU: every frame janky, GPU percentiles 4950 ms. Gross check only.

| Log | Run | Frames | Janky | p50 | p90 | p95 | p99 |
|-----|-----|--------|-------|-----|-----|-----|-----|
| OFF | 1 | 155 | 153 (98.71%) | 200 | 350 | 400 | 750 |
| OFF | 2 | 147 | 146 (99.32%) | 150 | 300 | 350 | 400 |
| OFF | 3 | 158 | 155 (98.10%) | 150 | 300 | 350 | 400 |
| ON  | 1 | 166 | 164 (98.80%) | 150 | 300 | 400 | 550 |
| ON  | 2 | 151 | 150 (99.34%) | 150 | 300 | 350 | 400 |
| ON  | 3 | 159 | 155 (97.48%) | 150 | 250 | 250 | 350 |

Both states within the same range (p50 150-200 ms, p90 250-350 ms, p99 350-750 ms); no discriminating signal.
Host load at those runs: about 11 (OFF) and about 11 (ON).

## App PSS after 1 min idle on Home (force-stop, start, 75 s, `dumpsys meminfo`)
| Log | TOTAL PSS (KB) | TOTAL RSS (KB) | Java Heap PSS | Native Heap PSS |
|-----|----------------|----------------|---------------|-----------------|
| OFF | 220,568 | 354,308 | 27,720 | 42,052 |
| ON  | 223,737 | 358,324 | 29,844 | 42,740 |

ON - OFF = +3.2 MB PSS (+1.4%), one sample each (single run per state; host load about 11 both).

## APK size
devDebug `app-dev-debug.apk`: 36,307,602 bytes (S1 base was 36,236,318; +71 KB from the merged telemetry-core
at cd16eb47 vs its own base, expected). Minified not built here (see telemetry-core baseline: assembleDevRelease
needs a keystore in a worktree; devDeploy / devBenchmarkRelease substitute).

## Method for the later comparison
Same emulator, devDebug, install-preserve, toggle via D-pad. Run the OFF and ON cold-start blocks back to back
(OFF, ON, OFF, ON) at host load below about 8 and compare median TotalTime (12 runs); discard blocks whose load
average crosses 10. Frames: same key sequence, gross check only; the real instrument is the new `frames` event.
PSS: same 75 s idle, take at least 3 samples per state.

## Notes
- During navigation to Settings one stray OK press opened a Details screen for the Home hero (no playback, left with
  BACK; no account mutation). Later runs used LEFT first to reach the sidebar.
- The telemetry directory holds many session files from these cold starts (device-local, not in git).
