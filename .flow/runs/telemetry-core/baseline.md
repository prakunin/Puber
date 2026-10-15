# Baseline: telemetry-core (perf), unchanged base 39903d31

Device: emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU, logged in). Package com.kino.puber.stage.
Build: worktree telemetry-core own devDebug APK, versionName 1.10.0-dev, sha256 9de4ee4a...f9f4,
installed via install-preserve (outcome installed, compatible_replace, no destructive action).
Lease taken and released.

## Cold start (am start -W, force-stop + 2 s, 7 runs, 6 s gap)
TotalTime ms: 2799, 2856, 2658, 3044, 2684, 3038, 2804
Sorted: 2658 2684 2799 2804 2856 3038 3044 ; median 2804 ms ; min 2658 ; max 3044 (spread ~14%).
WaitTime: 2834 2869 2668 3071 2705 3062 2823.
No reportFullyDrawn in the code at base, so this is time to first activity frame (Home content
arrives later over the network); not time-to-Home-content. Debug build (debuggable), so absolute numbers are
high; compare only against devDebug on the same emulator.
AC-9 time-to-content must come from the new `ready` event, not from this number.

## APK size
- devDebug: 36,236,318 bytes (app-dev-debug.apk)
- minified: assembleDevRelease FAILS in a worktree (SigningConfig "release" missing storePassword; no
  keystore.properties). Substitute: assembleDevDeploy (initWith release, R8 + resource shrinking, debug signing):
  25,155,146 bytes (app-dev-deploy.apk). Prod flavor not built. Deploy APK was not installed.

## Frame stats (dumpsys gfxinfo reset; Home: 6 Down, 8 Right, 8 Left, 6 Up with 0.3-0.4 s gaps; 3 runs)
Total frames / janky: 161/161 (100%), 148/146 (98.65%), 154/154 (100%).
Frame time p50 150 ms, p90 250-300, p95 300, p99 350-800 ms.
Not usable as a discriminating metric: the emulator renders in software (every frame janky, gpu percentile
4950 ms). Use only as a gross regression check on the same emulator; the series' own `frames` event (S3) is the
real instrument.

## Method for the later comparison
Same emulator, devDebug, install-preserve, 7 force-stop cold starts with identical gaps, compare median TotalTime
with the log OFF (expect within the 2658-3044 spread) and ON; compare devDeploy APK size.
