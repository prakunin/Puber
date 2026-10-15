LENS: edge  ROUND: 3  BASE: 39903d31 (incremental 0f20e63a..bbe17d3e)

### F1 [major] `first_content` is never recorded when the startup tab is Movies, Series or any other content-list tab
- Where: app/src/main/java/com/kino/puber/ui/feature/contentlist/model/SectionState.kt:7-18 (not in the diff); marker check at app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:99
- Problem: Content states are marked only for Home, Auth, Favorites, Bookmarks, History, Collections and Search. `getStartupTabOptions()` also offers Movies, Series, For4k, Concerts, DocMovies, DocSeries, TvShows (plus Cartoons, Anime when enabled), all mapped to `screens.contentList(type)` (MainUIMapper.kt:107). `ContentListVM` holds one data class state (never emits `ready`); `SectionVM` moves Loading -> Content/Empty but `SectionState.Content`/`Empty` do not implement `StartupContentState`. Regression from the row-2 fix.
- Failure scenario: startup tab Movies, recording on; sections load in ~1.5 s; nothing marks; after 60 s the fallback writes `startup` with `first_content = null`. AC-9 has no value for every content-list startup tab.
- Fix: make `SectionState.Content` and `SectionState.Empty` implement `StartupContentState`; add a test that every `TabType` in `getStartupTabOptions()` has a marked content state.

### F2 [minor] The uptime bound resets on every process start, so a future `enabled_at` still blocks auto-off across restarts
- Where: TelemetrySink.kt:204-210 (`expired`), :244-249 (`remainingMs`); TelemetryRuntime.kt:38-48
- Problem: `originElapsedMs` is taken per `sink.start()`, i.e. per cold start; the bound is "7 days in this process", not "7 days of recording". TV processes rarely live 7 days.
- Failure scenario: clock 2027-10-15 at enable, corrected to 2026-10-15, app restarted daily: nothing expires for ~1 year (AC-4); row shows "up to 7 days left" after each launch.
- Fix: persist accumulated recorded uptime across processes and expire on total >= 7 days; or treat an `enabled_at` far ahead of a synced clock as invalid.

Holding: row 1 (export deletes every non-.jsonl before zipping; at most one archive; .tmp never matches), row 4 (producers use elapsedRealtime; origin filter removes stale events).
