# Verify device evidence: cache-targeted-invalidation (round 1)

Date 2026-11-22. Head c0da5da7 (branch prakunin/cache-targeted-invalidation), base 40762767. Device emulator-5554 (AVD puber_tv_36, API 36 TV), lease held and released through tools/emulator-resource-lock.sh. Installs with tools/android-apk-install-preserve (outcome installed, compatible_replace, destructive_action false, same signer 6b2918...). No uninstall, data clear or downgrade.

## Builds

| Build | What | APK sha256 (on-device base.apk equal to local) |
|---|---|---|
| base-shim | 40762767 main sources + temporary measure shim | d8b61858f86418f7e7197649ef6b56307c424a85c341169b52852e0287b1425c |
| head-shim | c0da5da7 + the same shim | 9e0bfb2ae7d76413b57b0c585b461fe12aa2853744761e5cae661c897042dec0 |
| final | clean c0da5da7 (no shim), left installed | f5ddecacd324e906bbc99ed2e18fc533ea0f831dffcaf501732f2ecb67274cd7 |

Shim (uncommitted, reverted; `rg PUBERBASE app/src` prints nothing and `git status` is clean): PUBERBASE log lines in ContentCacheRepository.markItemDetailsStale, TvHomeSyncCoordinator.refreshNow, ItemDetailsRepository.fetchItem (detailsNetworkLoad), PlayerInteractor.saveWatchingTime (saveOk, plus the baseline's ApiError.Serialization ignore), PlayerVM drain catch (saveFail), exitPlayer entry (exitStart), just before router.back (routerBack), and two Choreographer frame callbacks after router.back (frame1, frame2). The same lines are on both builds, so exit times are comparable. For the base build the three changed main files (PlayerInteractor, PlayerVM, CachedFeed) were taken from 40762767 with git checkout and restored to HEAD afterwards. Marker logs only, no raw logcat: *-verify-base-markers.log, *-verify-head-markers.log.

## AC-10 (perf): 29 min of playback, one next-episode switch, then exit

Title Odd Taxi (id 76651), same device, same debug build type. Stream limit seen on this emulator: every episode stalls at 1:30 until a seek (same as the baseline's "Source error"); one seek forward unsticks it. Base: ep 3 from 10:41:40, switch to ep 4 at about 10:58:30, exit 11:10:39 (29.0 min in front). Head: ep 5 from 11:13:25 (stalled 1:30 to 1:50 until a seek), switch to ep 6 at about 11:35, exit 11:42:55 (29.5 min in front).

| Counter while in front | Base 40762767 | Head c0da5da7 |
|---|---|---|
| successful progress saves | 58 | 58 |
| markItemDetailsStale | 58 | 0 |
| TvHomeSyncCoordinator.refreshNow | 59 (58 saves + 1 Watched at episode end) | 0 |
| details network loads | 3 (next-episode prefetch, one at the switch, prefetch of the following episode) | 2 (one 24 s after start, one prefetch); none at the switch |

At exit: base 1 save, 1 mark, 1 refreshNow (2.2-3.3 s after exitStart, debounce), 1 details load by the Details/Home below; head 1 save, 1 mark, 1 refreshNow (+2.2 s), 1 details load. Same.

### Exit time, Back to the screen below (exitStart to routerBack, and to second frame after router.back), 5 exits each

The first exit of each build is the long session; exits 2-5 are 14 s sessions of episode 2 started from Details (a save at exit each time). Times are ms, dominated by the final progress-save network round trip.

| Exit | Base routerBack / frame2 | Head routerBack / frame2 |
|---|---|---|
| 1 (long) | 233 / 432 | 162 / 498 |
| 2 | 1252 / 1631 | 1134 / 1590 |
| 3 | 905 / 1328 | 898 / 1258 |
| 4 | 889 / 1159 | 1239 / 1539 |
| 5 | 1027 / 1328 | 881 / 1217 |
| median | 905 / 1328 | 898 / 1258 |

Pass line (base median + 150 ms): 1478 for frame2, head 1258. PASS. An extra base exit from a stalled player (10:39:58) read 979 / 1346 and is not in the table.

## AC-4 / M3: background

Head, player in front (episode 7, resumed), HOME pressed at 11:48:25: saveOk at 11:48:25.4 and markItemDetailsStale in the same millisecond, refreshNow 2.0 s later. One mark, one refresh. Reopening the app (am start): no marker (no second refresh, no second mark), resume dialog "Resume from 5:20?" shown.

## AC-6 / M1, M2: Home-launched session

Head, deep link puber://content/items/76651/seasons/1/episodes/7?action=play from Home (no Details below), seek forward, two 30 s saves in front (markers: 2 saveOk, 0 mark, 0 refresh), BACK to Home: exitStart, saveOk, markItemDetailsStale 1, routerBack 6 ms later, frame2 +435 ms, a details load for the item right after, refreshNow +2.2 s. Home shows (focus on the card, row bar updated, no crash). Opening Details afterwards (about 20 s later): no new network load (record fresh after the post-exit load), Home card progress bar advanced.

Not observed: saved progress on the Details screen. On this account the item payload carries no per-episode progress (episode cards show no bar and the line reads "Up next - season 1, episode 2" for episodes with local resume points), with the base build as well; this is the marktime/ApiError.Serialization problem outside this run (resume-progress-anomaly). So the visible outcome of AC-6 cannot be seen here; the mechanism (exit mark, then revalidation before the next Details open) was.

## M4: next-episode switch

Base (3 to 4) and head (5 to 6): the next episode started and played on its own, no stall, no crash; head had no details load at the switch.
