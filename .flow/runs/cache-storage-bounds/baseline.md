# Baseline: cache-storage-bounds

Unchanged build at base a22c03bc (worktree clean). Dev debug APK sha256 ea6b517688c4...d109d86 (version 1.12.3-dev), installed with install-preserve (outcome installed, compatible_replace, no destructive action); on-device base.apk sha256 matches. Device: emulator-5554 (AVD puber_tv_36, API 36, 1920x1080, heapgrowthlimit 192m, heapsize 512m), lease held and released. Date 2026-11-21. Note: the emulator process had died mid-session and was restarted (headless, `-read-only`); app, APK and data were intact afterwards (same sha256, same databases).

## Media cache (AC-1, AC-2, AC-3, AC-11)

- Starting state: `media_cache` = 526360 KB by du (about 514 MB, 440 files), i.e. at the 512 MB cap. AC-3 starting condition (more than 256 MB) already holds.
- Because the cache is full, du stays flat during playback (eviction offsets writes). Bytes written are therefore measured as the total size of files that did not exist before playback (file list before vs after, `find -printf` over adb).
- Playback: series (Season 1, episode 1), started from the 4K tab with OK, main player only, about 6.5 min, no next-episode warm-up reached. Single run.

| Minute | new files | new MB | evicted files | evicted MB | total MB |
|---|---|---|---|---|---|
| 0 | 11 | 8.8 | 12 | 14.7 | 530.3 |
| 1 | 35 | 45.2 | 40 | 45.6 | 535.9 |
| 2 | 51 | 65.9 | 56 | 67.2 | 535.0 |
| 3 | 51 | 65.9 | 56 | 67.2 | 535.0 |
| 4 | 67 | 81.9 | 76 | 81.5 | 536.6 |
| 5 | 83 | 103.6 | 96 | 103.2 | 536.6 |
| 6 | 99 | 127.5 | 106 | 128.3 | 535.5 |

Main playback wrote about 127 MB in 6.5 min (about 20 MB/min, bursty, plateau at minute 2-3); du after: 525564 KB. For AC-1 the after-value must be 0 new bytes in the same measure; extrapolated 30 min is about 590 MB at this rate (single sample, rate depends on the stream).

## cached_payload and puber.db (AC-4, AC-5, AC-7, AC-11)

- Schema version 3 (user_version 3); tables android_metadata, watch_state, watch_state_sync, cached_payload, room_master_table.
- Before: 2870 rows, payload total 24,528,435 bytes, puber.db 29,618,176 bytes (7230 pages of 4096, no free pages). updated_at range 2026-10-10 .. 2026-11-21.
- Rows older than 7 days (HardCeiling): 2444 rows, 15,319,229 bytes of payload (85 percent of rows; never swept today).
- Key prefixes: content:v1:item 2711 rows (24.49 MB), content:v1:query 156 (0.03 MB), content:v1:payload 3 (0.01 MB). Rows not starting with content:v1: (legacy): 0.
- After a fixed session (cold start, 5 x 18 D-pad steps on Home, Series tab, one series details open, 6.5 min playback): 2882 rows, 24,714,378 bytes, puber.db 29,835,264 bytes. A 2000-row cap would delete about 880 rows at the first sweep.
- Legacy cleanup cost: this database has 0 legacy rows, so the six removeByPrefix calls (home:, item:, similar:, section:, watchlist:, history:) delete nothing here; they run once per process on the first cache access. Per-call duration was not measured on device (no instrumentation at base); not captured.

## Coil memory cache and memory (AC-9, AC-11)

- Cap at base: maxSizePercent 0.15 of the app memory class (largeHeap="true"). With heapgrowthlimit 192m / heapsize 512m on this emulator that is about 77 MB if the large class (512m) is used, or 29 MB for the normal class; the exact runtime value was not read (no debug log or MemoryProbe output: telemetry is off, last telemetry file from 2026-10-15). Not captured on device.
- dumpsys meminfo (KB, PSS / Java heap alloc / native heap alloc):
  - after cold start, Home loaded (about 15 s): PSS 254142, Java 49932, native 45836
  - after the Home D-pad scroll session: PSS 276232, Java 45600, native 68504
  - during playback (after about 6.5 min): PSS 306063, Java 85232, native 47664
- Home scroll screenshot showed posters loaded; no placeholder churn observed.

## Startup

- am start -W TotalTime, cold, same build, emulator under load from another worktree: 2059, 2361, 4721 ms (first session), 6385 ms (after emulator restart, cold boot). High spread, not a stable baseline; startup marks (telemetry) not captured.

## Not measured

AC-2 warm-up bytes and onCachedBytesRead (needs a debug listener, not in base; also needs a longer episode run), Coil maxSize runtime value, legacy cleanup duration, startup marks.
