# Baseline: cache-shared-http-stack (attempt 3)

Commit: dd19f70d (plan Task 1, "Count network events per client in debug builds and give Coil an explicit image fetcher"). Dev debug APK sha256 3c90224a5fe92db31a40162e8dfeb5a77a03a94eb785e5f1662dc99593b6694e (built after the commit; on-device base.apk sha256 matches).
Device: emulator-5554 (puber_tv_36, API 36 TV), lease held and released. Install: install-preserve, outcome installed, compatible_replace, no destructive action. Room DB is v4 (SQLite user_version 4 read from the file header).
Counts only; no raw logcat kept.

## M3: KinoPub HTTP cache (app label, cumulative NetStats line at session end)

Steps: cold start, Home, series Details (bookmarks row), open season 8 episode list entry, playback started (about 15 s, first frame at 0:03), BACK twice to Details. Playback started by mistake of my navigation (the brief said no playback; M3 step 5 asks for it). No account mutation was made beyond what watching does.

| app | value |
|---|---|
| call | 40 |
| dns / connect | 12 / 12 |
| cacheHit | 1 |
| cacheConditionalHit | 1 |
| cacheMiss | 21 |

doh: call 10, dns 2, connect 2. image: no events in this session (no image fetch reached the network; Coil disk cache warm).
cache/okhttpcache after the session: 3 files (one cache entry: 2 data files + journal), about 31 KB. Both hit and conditional hit are non-zero. The first cold start alone logged hit=1 cond=1 within its first 16 app calls.

## Perf "before" (M5)

Session per test-design: force-stop, launch, 12 s Home, RIGHT/DOWN x2/RIGHT, CENTER into Details, BACK, LEFT x3, DOWN x4, CENTER (Movies hub, a single horizontal row, so DOWN x5 did not move focus), DOWN x5. Cumulative NetStats line read at the end. Warm-up run uncounted (after M3).

| run | doh.call | image.dns | resolver (doh.call + image.dns) | connect app/doh/image | connect sum | app hit/cond/miss |
|---|---|---|---|---|---|---|
| warm-up | 4 | 5 | 9 | 3/2/5 | 10 | 1/1/20 |
| 1 | 2 | 1 | 3 | 3/2/1 | 6 | 1/0/15 |
| 2 | 4 | 0 (no image events) | 4 | 3/2/0 | 5 | 1/1/11 |
| 3 | 2 | 0 (no image events) | 2 | 3/2/0 | 5 | 1/0/10 |

Medians of 3 counted runs: resolver queries 3, new connections 5. Spread: resolver 2-4, connections 5-6. The spread is driven by how many images Coil fetches over the network (Coil disk and memory caches), so the image label is often absent after the warm-up.

Secondary: `am start -W` TotalTime cold starts (ms) 1900, 1844, 1845, 1816, 2120; median 1845 ms.

## Verdict

KinoPub HTTP cache: KEEP

(cacheHit 1 and cacheConditionalHit 1 in M3; cacheHit 1 in every perf run. Drop only if both are zero.)
