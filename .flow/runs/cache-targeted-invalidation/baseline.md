# Baseline: cache-targeted-invalidation (AC-10)

Date 2026-11-22. Mode baseline, track perf. Base 40762767, worktree clean apart from a temporary measure patch (reverted after the run; kept as `.flow/runs/cache-targeted-invalidation/baseline-measure.patch`).

## Build and device

- Build: worktree own `app-dev-debug.apk` (dev debug, 1.12.4-dev, versionCode 2090000000), base 40762767 + measure patch. Installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, no destructive action). On-device sha256 of base.apk equals the local APK: `a5e0bfdc60f18027bf29d7243fd7269d992d95c0f18168ea6a6f9d468df6fe2f`.
- Device: emulator-5554 (AVD puber_tv_36, API 36 TV). It was found dead at the start of the run and was booted headless without wipe (`-no-window -no-snapshot-save`); app data and login were intact. Room database `puber.db` user_version = 4 (master v4).
- Lease: acquired and released through `tools/emulator-resource-lock.sh`.
- Title: "Odd Taxi" (id 76651), series, 13 episodes, resumed from a saved position; one next-episode switch (episode 1 -> 2) happened by itself during the window.

## Method

Temporary uncommitted `Log.i("PUBERBASE", ...)` lines (patch file above):

| Marker | Place |
|---|---|
| `markItemDetailsStale` | `ContentCacheRepository.markItemDetailsStale` |
| `refreshNow` | `TvHomeSyncCoordinator.refreshNow` |
| `detailsNetworkLoad` | `ItemDetailsRepository` just before `api.getItemDetails` |
| `saveOk` / `saveFail` | after the API write in `PlayerInteractor.saveWatchingTime` / in the `PlayerVM` drain catch |

Streamed with `adb logcat -v time -s PUBERBASE` (16 MiB buffer; the default 256 KiB buffer wrapped within ~9 min because of the request logging and lost the first attempt). Markers only; no raw logcat is kept.

### Measurement shim (needed, read this)

On this build and account the `watching/marktime` call returns HTTP 200 but the response body fails to decode (`ApiError.Serialization: Malformed response`). `PlayerVM` swallows that as "save failed", so `saveWatchingTime` throws before `markStale` and `markContentChanged` run. The server did store the position (the resume dialog showed the new position after reopening). So with the unmodified base build the per-save effects never fire on this emulator and the baseline would read 0, which is not the behaviour on a working account. For the measurement only, `saveWatchingTime` ignores `ApiError.Serialization` from `setWatchingTime` (patch, line 1 of the interactor hunk). The same shim must be applied to the "after" build, otherwise "after" also reads 0 and shows nothing. This looks like part of `resume-progress-anomaly` ("progress written but no resume"); not investigated here.

## Result (base, emulator, in-front playback)

The 30 min target was not reachable: after the episode switch, episode 2 failed with ExoPlayer "Source error" at 09:30:44 device time (and again after two Retry presses, stuck buffering at 1:30). Effective in-front window: **09:23:10 to 09:30:44 = 7.6 min** (resume at 18:xx of episode 1, auto switch to episode 2 at ~09:29:07).

| Counter (window 09:23-09:30:50) | Count | Rate |
|---|---|---|
| successful progress saves (30 s tracker) | 15 | 2.0 / min |
| `markItemDetailsStale` | 15 | 2.0 / min (one per save) |
| `TvHomeSyncCoordinator.refreshNow` | 16 | 2.1 / min (15 per save, +1 after the episode end Watched change, 09:28:03) |
| details network loads | 3 | all for the playing title: 09:28:00 prefetch of the next episode (right after a mark), 09:29:06 the episode switch (`getItemDetails` waited for the refetch), 09:29:49 prefetch of the following episode |

Extrapolated to the brief's 30 min of playback: about 60 marks, about 60 refreshes, and a details refetch at every next-episode switch and prefetch, since each save re-marks the record stale.

Exit (Back pressed at 09:49:21 from a stalled player, one more save pending): 1 save, 1 `markItemDetailsStale`, 1 `refreshNow` (2 s debounce, 09:49:24), 1 details network load (the Details screen below reloads forced, `DetailsVM` returns-change path, 09:49:22.7).

Back to the screen below: Details content visible in the first screenshot frame at about 1.5 s; the frame at 0.7 s was still the player (frames ~0.8 s apart, adb screencap resolution, one run). Coarse; do not compare below ~1 s.

Not measured: start wait of the next episode in seconds (no start marker in the temporary log; the `getItemDetails` network refetch at the switch is what AC-10 expects to disappear). Spread: single 7.6 min run, saves landed at 30.0 s intervals (± 0.3 s), so the per-minute counts are deterministic; a repeat adds nothing except the switch timing.

## Account mutations

Only normal playback: watching/marktime saves for Odd Taxi episode 1 and 2 (progress moved forward; the title now shows 1 episode watched) and the Retry/Back presses. No watched toggles, bookmarks or other writes by hand.

## Evidence files

- `docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-baseline-markers.log` (PUBERBASE markers only).
- `docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-baseline-device.md`.
