LENS: edge  ROUND: 1  BASE: a97ee2b1

### F1 [minor] The API `forced` flag overwrites the manifest FORCED flag, so the selector can pick the wrong track
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackMerger.kt:143-148 (`withApiMetadata`); app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackSelector.kt:57 (`forcedMatches`)
- Problem: when a manifest row is linked to an API row, it takes `isForced = apiTrack.isForced ?: isForced`. The `PlayerTextTrack` candidate still reads FORCED from the player's `format.selectionFlags`. The selector's semantic filter requires `candidate.isForced == track.isForced`, so a disagreement removes the right candidate and keeps the wrong one.
- Failure scenario: an HLS title has two `ru` renditions: a full one, and `FORCED=YES` whose `<file>/index.m3u8` matches an API row with `"forced": false`. (1) The merger writes `isForced = false` onto the forced row; the picker shows two plain "Russian" rows with ordinals. (2) The viewer picks the forced row; the filter drops the forced candidate and keeps the full one. (3) id, identity and group rules miss. (4) `matchByLanguage` returns the full track. The full subtitles are shown instead of the forced ones.
- Fix: for `fromManifest` rows, keep the player's flag (`isForced = if (fromManifest) isForced else apiTrack.isForced ?: isForced`). Or make the selector compare forced against the player's own flag.

### F2 [minor] Re-finding the row by group id and track index ignores language, so after a reorder it can land on another language
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt:55-60, 80-83 (`hasSameTrackGroupAndIndex`); used from PlayerVM.kt:534-558
- Problem: the selector runs a semantic filter (language and forced) before the group-id fallback. `findSameSubtitleTrackIndex` does not: the group-id + track-index rule runs before the language rule without a language check. Side-loaded group ids are positional (`1:0`, `2:0`, ...), and a refresh can reorder them (R3).
- Failure scenario: an HLS item whose API rows have no `file` and opaque token URLs, so `playerTrackId` and `url` both change on `refreshStreamLinksOnce`. (1) Viewer picks side-loaded Russian, group `2:0`. (2) Links expire; the refreshed list puts English second. (3) Rules 1-3 miss; rule 4 matches `2:0`/0, now English. (4) The VM calls `selectSubtitle(English row)`. Rule 5 (the unique Russian row) would have been correct.
- Fix: in `hasSameTrackGroupAndIndex`, also require `sameSubtitleLanguage` (when both known) and a compatible forced flag, or apply the selector's semantic filter first.

### F3 [minor] An API subtitle with an empty `url` is now selectable and side-loaded with an empty URI, where it used to mean Off
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackMediaItemFactory.kt:38-49; app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:41-48; PlaybackController.kt:569 (`isOff`)
- Problem: before, `url.isEmpty()` meant Off in the controller. Now `isOff` also needs an empty `language`, so `{"lang":"rus","url":""}` becomes a real row. The factory side-loads it as `SubtitleConfiguration(Uri "")`, now also on HLS. No code filters blank URLs.
- Failure scenario (progressive): the viewer picks the row; `matchByLanguage` picks the empty-URI child when it is the only `ru` track. The load fails (D15 path). The picker says "Russian" and nothing renders, with the R2 rebuffer risk while retries run. On HLS the empty-URI child is also merged in and listed.
- Fix: drop links with a blank `url` in `PlaybackMediaItemFactory.spec` and `PlayerUIMapper.mapSubtitleTracks`, and set `currentItemHasSideLoads` from the filtered list.

Checked and handled (not reported): audio-less streams; empty API list on HLS; numeric GROUP-IDs (`0:1:English` -> `1:English`); manifest-only rows with empty url not Off; re-send loop (deterministic rows); HLS<->progressive fallback (re-find, Off when lost); restore over merged rows (R4 acknowledged); failing side-load (AC-9 / D15).
