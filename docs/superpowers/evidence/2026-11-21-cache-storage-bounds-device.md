# cache-storage-bounds: device evidence (2026-11-21)

Build: worktree dev debug APK, sha256 a8e0b4ea1a33...bba599 (1.12.3-dev), installed with install-preserve (outcome installed, compatible_replace, destructive_action false); on-device base.apk sha256 matches the build. Device: emulator-5554 (AVD puber_tv_36, API 36), lease held and released. Baseline: .flow/runs/cache-storage-bounds/baseline.md (base a22c03bc, same device).

Account mutations: playback of Season 1 episodes 7 and 8 of one series (resume position saved by the app, watch_state 885 -> 886 rows), a few minutes in total. Nothing else.

Upgrade preparation: before install the v3 puber.db (2900 rows) was pulled, six legacy-prefixed fixture rows (home:, item:, similar:, section:, watchlist:, history:, fresh timestamps) were added on the host and the file was put back with run-as while the app was stopped (a backup copy was kept). The app was not uninstalled and no data was cleared.

## AC-7 Room 3 -> 4 over real data (and legacy sweep)
- Steps: install-preserve, cold start, read puber.db afterwards.
- Expected: user_version 4, the six legacy rows gone, content:v1: rows intact, watch_state and watch_state_sync identical.
- Observed: user_version 3 before, 4 after; 2906 rows before (2900 content:v1: + 6 legacy), 2900 after with 0 non-content:v1: rows; index_cached_payload_updated_at present; watch_state 885 rows and watch_state_sync 1 row, row-content sha256 identical before and after; app opened Home, login intact. PASS.

## AC-4 / AC-5 age and row sweep on the real table
- Steps: 60 s after cold start, read the table (rows older than 7 days: 2443 of 2900; cap 2000 not reached by this data).
- Observed: 2900 -> 457 rows (payload 24.6 MB -> 9.2 MB), 0 rows older than 7 days left, every remaining row of the same key set. Cap step exercised by instrumented and unit tests only. PASS for the age step on device.
- After a later ON_STOP (HOME key): 458 rows, no crash (0 FATAL entries in the session log).

## AC-1 main playback is read-only
- Steps: Home, Continue watching, series details, Play (Season 1 episode 8, the last episode, no next episode), play about 7 min with the main player.
- Expected: no new files in media_cache. Baseline measure: 127 MB of new files in 6.5 min.
- Observed: file list at 15 s and every minute for 7 min: 0 new files, du constant 261300 KB. Compared with the pre-launch list: 0 new files. PASS for a 7 min session (30 min not played; the baseline plateau and the zero growth make a later write unlikely but it is not shown).

## AC-3 cap 256 MB
- Observed: du of media_cache 526360 KB (440 files) before the new build; 261300 KB (255.2 MiB) right after the first playback start; 254672 KB after warm-up writes; debug log cacheSize=259464846 bytes (247.4 MiB). PASS (at or below 256 MiB).

## AC-2 warm-up writes, next episode reads from cache
- Steps: play episode 7 (resumed at 46:45), seek with D-pad right to the last minute, let the episode end and episode 8 start.
- Observed: 7 new files written during the warm-up (sizes 23503, 82365, 3067670, 66552, 1459256, 82447, 168824 bytes); at the start of episode 8 the debug log shows 7 onCachedBytesRead lines with cachedBytesRead exactly those values. PASS.

## AC-9 Coil memory cap
- Observed (debug log at cold start): Coil memory cache maxSize=50331648 (48 MiB). PASS.

## AC-11 numbers (single run each, same emulator)
| Metric | Base | New |
|---|---|---|
| media_cache new files in main playback | 127 MB in 6.5 min | 0 in 7 min |
| media_cache du | 526360 KB | 261300 KB, 254672 KB after warm-up |
| cached_payload rows before / after session | 2870 / 2882 | 2900 / 458 (after sweep) |
| payload bytes | 24.5 MB / 24.7 MB | 24.6 MB / 9.8 MB |
| puber.db size | 29.6 MB / 29.8 MB | 29.7 MB / 10.8 MB (main file) |
| Coil memory cap | not read (0.15 of memory class, about 29 or 77 MB) | 48 MiB |
| PSS after cold start | 254142 KB | 254266 KB |
| PSS after 5x18 D-pad Home scroll | 276232 KB | 301027 KB |
| cold start am start -W | 2059, 2361, 4721 ms (emulator loaded) | 1604 ms (one run) |

PSS after the scroll is 25 MB above the base sample; the base session scrolled on a warm process and the sample is single, so no regression is claimed or excluded. Posters were loaded in the screenshot after scrolling, no placeholder churn seen.

## Instrumented tests (make DEVICE=emulator-5554 GRADLE=./tools/agentw itest)
- CachedPayloadDaoTest: 10 tests, 0 failures.
- PuberDatabaseMigrationTest: 2 tests, 0 failures (migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow, productionBuilderOpensAV3DatabaseWithoutDroppingWatchState).
- After the runs the installed APK sha256 is unchanged and the app still opens logged in.

## Round 2 (head a8fdd8d1): media cache trim on the first sweep

Device: emulator-5554 (AVD puber_tv_36), lease held and released. While waiting for the lease another agent installed a different build of the same package; the worktree APK was then installed with install-preserve (outcome installed, compatible_replace, destructive_action false), app still logged in, puber.db opened. media_cache lives in the external cache dir (/sdcard/Android/data/com.kino.puber.stage/cache/media_cache).

### AC-3 / trim with no warm-up and no playback
- Precondition: the cache left by round 1 was already at the 256 MB cap (261300 KB by du), so a cache above the cap could not be made by legitimate means (main playback no longer writes, warm-up evicts to the cap). To exercise the trim path a temporary local build with MEDIA_CACHE_SIZE_BYTES = 128 MiB was made (one constant, never committed, worktree restored with git checkout afterwards; git status clean) and installed with install-preserve over the 256 MB cache.
- Steps: force-stop, cold start, no input, no playback, du every 30 to 90 s.
- Expected: media_cache shrinks to at most 128 MiB without a write by warm-up.
- Observed: 261300 KB (216 files) at start and at 30 s; 130348 KB (127.3 MiB) at 70 s and at 90 s; 0 FATAL entries, no "Media cache trim failed" warning. PASS for the trim mechanism (cap value 128 MiB in the test build, not the shipped 256 MiB).
- Shipped build (256 MiB cap, worktree APK, on-device sha256 equals the built APK): cold start, 75 s idle: du unchanged at 261300 KB (under the cap, trim is a no-op), no crash. The shipped-cap trim of an over-256 MB cache was not seen on device; the over-cap state needed a smaller cap to be reachable.
- After the checks the shipped build was reinstalled; media_cache stays at 130348 KB (a cache, refills through warm-up).

### AC-6 smoke
- The sweep ran once after the startup delay in each session (trim happened at about 60 s, see above), 0 FATAL entries.

### AC-9 smoke
- Debug log at cold start: Coil memory cache maxSize=50331648 (48 MiB). PASS.

### AC-1 / AC-2
- Not re-run on device in round 2: the commit under test changes only the trim and the extraction of cacheWriteSink (same expression as before). Unit coverage: CacheWriteSinkTest. Round 1 evidence above stands.
