# Side-load API subtitles on HLS and merge them with manifest tracks

Status: approved
Date: 2026-10-14
Slug: subtitle-sideload-merge
Kind: feature
Size: M
Tracks: none

## Problem / Why

<!-- owner: analyst (brief.md, written against be8395a4; the first batch is now merged in 50f46021) -->

Request: "Port the rest of upstream's subtitle work: side-load API subtitles on HLS (003d4c55) and the full subtitle merge model; starts after subtitle-upstream-port lands."

- Root cause of the HLS gap, confirmed: `PlaybackController.kt:651-675` builds HLS with `HlsMediaSource.Factory(...).createMediaSource(mediaItem)`. That factory never reads `MediaItem.subtitleConfigurations` (Media3 1.11.0 source, research.md: 5), so the API subtitles attached at `:599-614` are dropped on HLS. Only progressive (`player.setMediaItem`) side-loads them via `DefaultMediaSourceFactory`. Present since `88cdf071`.
- The VM never sees player text tracks: `PlaybackControl.Callback.onTracksUpdated(audioTracks, selectedIndex)` is audio only (`PlaybackController.kt:67`, impl `PlayerVM.kt:211`). The picker comes from the API list only (`PlayerUIMapper.kt:35-75`).
- `MergingMediaSource` rewrites `format.id` / group id to `"<n>:<id>"`, so after side-loading, matching by `format.id == stableKey` (`PlaybackController.kt:866-942`) fails and only the label match works. Matching and selection must strip the prefix (research.md: 5).
- Warm-up player builds HLS with the same shape (`PlaybackController.kt:748-764`); whether it must change for cache hits is unverified.
- Missing from master: `SubtitleTrackMerger`, `SubtitleTrackSelector`, `SubtitleLabeler`, `SubtitleLanguage`, `PlaybackMediaItemFactory` (only `SubtitleTrackIdentity.kt` `stableSubtitleKey()` exists).
- Fork already has `StreamType` on `StreamCandidate` (`PlayerInteractor.kt:19-26,232-245`), so upstream f38fbcf0 / dc011a81 are not needed.

## Acceptance criteria

<!-- owner: analyst (brief.md) -->

AC-1. Given an HLS stream whose API subtitle list contains a language the manifest does not declare, When playback starts, Then that subtitle appears in the picker and can be selected and rendered. (device)
AC-2. Given an HLS stream whose manifest declares a language that the API list lacks, When the picker opens, Then the manifest subtitle is listed with a language-name label and is selectable. (device; unit for the merger)
AC-3. Given the same language offered by both manifest and API, When rows are merged, Then exactly one source is kept per language: the one with more tracks, HLS on a tie; rows with unknown language are kept unless proven duplicates. (unit: `SubtitleTrackMergerTest`)
AC-4. Given side-loaded tracks whose ids carry the `n:` prefix from `MergingMediaSource`, When the user selects a row, Then the text track is selected by its prefix-stripped source identity (format id / stable subtitle key), falling back to the exact merged `TrackGroup.id` + track index and then to an unambiguous language; never by position. (unit: `SubtitleTrackSelectorTest`) _(reworded at gate 1, owner, Q1)_
AC-5. Given progressive streams, When playback starts, Then subtitle behaviour is unchanged (still side-loaded, Off by default, rows as before). (unit + device)
AC-6. Given the player reports text tracks, When tracks change (switch stream, rebuild), Then the picker rows and the selected row update through `PlaybackControl.Callback` without losing the user's selection when the same merged row still exists. (unit: `PlayerVM` tests)
AC-7. Given a stored per-item subtitle language/URL preference, When the item opens, Then the same restore as on master still selects the matching merged row (existing resolver behaviour kept). (unit, extend `PlayerVMTrackSelectionTest`)
AC-8. Given duplicate languages across rows, When labels render, Then labels use language names with AI/forced qualifiers and disambiguating ordinals consistent with the first batch's strings (no raw ids). (unit: `SubtitleLabelerTest`; wording `(owner judges)`)
AC-9. Given a side-loaded subtitle URL that fails to load (404/TLS), When the stream plays, Then playback continues without a fatal error and the video is unaffected. (evidence: the Media3 1.11.0 trace in Design D15; hermetic test in upstream-sync-triage P6; device check if such a title turns up) _(evidence method set at gate 1, owner, Q2)_
AC-10. Given the warm-up player and the main player, When HLS starts, Then time-to-first-frame is not regressed and the warm cache is still hit. (device observation; only if warm-up shape changes)
AC-11. Given the change, Then `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` passes and new unit tests (merger, selector, labeler, VM merge) are JUnit 5 with `FakeResourceProvider` where strings are mapped.

## Out of scope

<!-- owner: analyst (brief.md) -->

- Step 2 of option C (run `subtitle-restore-model`): `SubtitlePreference`, new forced and descriptive-label pref keys, restore across playback recreation, forced and language-less selection, old-to-new pref compatibility (brief.md: Options C).
- Upstream f38fbcf0 / dc011a81 (HLS detection, stream container): the fork already carries `StreamType` (brief.md: Findings).
- Instrumented player tests and fixtures (upstream-sync-triage P6).

## Design

<!-- owner: architect (design-architect.md) -->

Base: master `a97ee2b1` (first batch merged in `50f46021`). App paths are relative to
`app/src/main/java/com/kino/puber/` unless they start with `app/`. Every `path:line` was read in
the worktree `/home/user1/orca/workspaces/Puber/subtitle-sideload-merge`. Media3 citations are
to the `1.11.0` tag sources (`libraries/exoplayer/...`, `libraries/exoplayer_hls/...`), fetched
from `raw.githubusercontent.com/androidx/media/1.11.0/`. Upstream citations are
`git show b755826c:<path>` from the main checkout.

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | How are API subtitles side-loaded on HLS? | Drop the hand-built `HlsMediaSource.Factory` branch in `setMediaSource` (`ui/feature/player/vm/PlaybackController.kt:659-681`). Every stream goes through `player.setMediaItem(item[, startPositionMs])`, so the player's own `DefaultMediaSourceFactory` (`PlaybackController.kt:406-412`, built at `:471-483`) builds it. The item comes from a new `PlaybackMediaItemFactory` (upstream `b755826c:.../vm/PlaybackMediaItemFactory.kt`). It sets `MimeTypes.APPLICATION_M3U8` when `StreamCandidate.type == HLS`, and attaches every API subtitle with id = label = `stableSubtitleKey()` and FORCED from `SubtitleLink.forced` (the same body as `PlaybackController.kt:606-635`). | `HlsMediaSource.Factory.createMediaSource` reads only `streamKeys` (`HlsMediaSource.java:423`). `DefaultMediaSourceFactory.createMediaSource` wraps the main source and one source per `subtitleConfiguration` in a `MergingMediaSource` (`DefaultMediaSourceFactory.java:603-659`). The HLS delegate gets the same configuration the fork builds by hand today: the error policy (`:764-766`, set at `PlaybackController.kt:481`), chunkless preparation on by default (`HlsMediaSource.java:177`), subtitle parsing during extraction (`:179`; `DefaultMediaSourceFactory.java:738,771`) and GOP codecs (`HlsMediaSource.java:178`; `DefaultMediaSourceFactory.java:740,772-773`). The MIME type matters because the fork chooses HLS from `StreamType` (`domain/interactor/player/PlayerInteractor.kt:18-26`), not from the URL; without it `inferContentTypeForUriAndMimeType` (`DefaultMediaSourceFactory.java:563-566`) guesses. R8 keeps the reflective constructor through the AAR's consumer rules (`media3-exoplayer-1.11.0.aar!/proguard.txt:67-70`). |
| D2 | Do we apply upstream's `SubtitleLink.shouldSideLoad(isHls) = isHls \|\| embed != true`? | No. Side-load every API subtitle on both transports, as master does today. | AC-5 requires progressive to stay unchanged. Master side-loads all of them on progressive (`PlaybackController.kt:606-620`); the filter would drop `embed == true` rows there. |
| D3 | Add `SubtitleLink.file`? | Yes: `val file: String? = null` on `data/api/models/Models.kt:429-435`. Carried as `SubtitleTrackUIState.sourceFile`. | Upstream `d6709295` added it because a KinoPub HLS rendition URI (`<file>/index.m3u8`) does not share a path with the signed API `url`. Without it, manifest rows cannot be linked to API rows, so they carry no `url` and the existing restore by URL (AC-7) misses them. The change is additive and nullable, so old payloads still parse. |
| D4 | Which tracks does the controller report, and when? | Extend `PlaybackControl.Callback.onTracksUpdated(audioTracks, selectedIndex)` (`PlaybackController.kt:67`) with `subtitleTracks: List<SubtitleTrackUIState>? = null`. `null` means "no player rows, use the API rows". The controller sends a list only when the current stream is HLS, and `null` for progressive. `notifyTracksUpdated` (`:852-868`) returns early only when there are **no audio and no text groups**; today the guard is audio only (`:855`). | HLS-only merging keeps AC-5 literal: progressive rows stay the API rows. Upstream merges everywhere, which would also list container text tracks and regroup rows on progressive. The guard change is required: without it an audio-less HLS stream would never show merged rows (AC-1/2). Upstream made the same change (`b755826c:.../PlaybackController.kt` `notifyTracksUpdated`). |
| D5 | How does a player row say where it came from? | Controller-built rows carry `playerTrackId` = `format.id` **with the merge prefix stripped**, `playerTrackGroupId` = the full `TrackGroup.id` (prefix kept), `playerTrackIndex`, `descriptiveLabel` = `format.label`, `isForced` from the FORCED selection flag, and `playerTrackUri` = the matched `HlsMultivariantPlaylist` rendition URL. Rendition matching uses the prefix-stripped id, then label + language, with **no positional fallback**. Add an explicit `fromManifest: Boolean`. It is true when the group came from the main source, i.e. its id has no merge prefix or the prefix `0:`. | `MergingMediaPeriod` rewrites `Format.id` and `TrackGroup.id` to `"<childIndex>:<id>"` and leaves `label` alone (`MergingMediaPeriod.java:316-330`). Child 0 is the main source, and children 1..n follow the order of `subtitleConfigurations` (`DefaultMediaSourceFactory.java:606-659`). Upstream classifies "manifest" as `playerTrackUri != null` (`b755826c:.../SubtitleTrackMerger.kt:148-149`). Under that rule a manifest track whose rendition did not match counts as side-loaded, and the per-language count rule (AC-3) miscounts. Upstream also compares the prefixed `format.id` with the unprefixed rendition id, which can never match once side-loads exist. |
| D6 | `SubtitleTrackUIState` shape | Upstream's fields (`b755826c:.../model/PlayerUIModels.kt:12-36`) plus `fromManifest`, and an `isOff` extension. **Drop `index`**: nothing reads it (only `PlayerVM.kt:578` reads `action.index`, which belongs to the action). Remove `playerGroupIndex`: D8 drops the positional rule that was its only reader. | Merged rows are rebuilt on every report, so a stored index would just be one more thing to renumber. Upstream tests port without edits to the constructors. |
| D7 | Merge rule | Port `SubtitleTrackMerger` (`b755826c:.../vm/SubtitleTrackMerger.kt`, 154 lines) as is, with three changes. "Manifest" means `fromManifest` (D5). The prefix strip is shared with the selector. Labels come from the fork-fed `SubtitleLabeler` (D10). Off row first, then per language: keep the source with more tracks, HLS on a tie. Unknown-language rows are kept unless their source identity proves them a copy of a manifest row. Rows are enriched with API `url`, `sourceFile` and `isForced` by a unique identity match, and grouped by language in first-appearance order, forced last. | Owner decision Q3 (rule `9bd835b0`). |
| D8 | How does a row become a text track? | New pure `SubtitleTrackSelector` + `PlayerTextTrack` (vm package), ported from `b755826c:.../vm/SubtitleTrackSelector.kt` with two changes. (a) The rule order is: semantic filter (language, forced, readable label), then **source identity** (the candidate's prefix-stripped `formatId` or its `formatLabel` is `sameSubtitleIdentity` with the row's `url`, `sourceFile` or `playerTrackId`), then **full `groupId` + `trackIndex`**, then unambiguous language. Each step is reduced with `singleOrNull`. (b) Upstream's `matchByCoordinates` is **removed**. `PlaybackController.applySubtitleTrackSelection` (`:874-890`) calls the selector and keeps the fork's fallback for "no target": text enabled, preferred language set, no override (`:879-889`). `findTextTrack`, `findUnambiguousTextTrackByLanguage`, `findTextTrackBy` and `TextTrackSelection` (`:892-940`) move out. | The prefix-stripped **group** id is not unique. Every side-loaded child is a lazy single-track `ProgressiveMediaPeriod` whose group id is `Integer.toString(0)` (`ProgressiveMediaPeriod.java:1060`), so after merging they are `1:0`, `2:0`, … and all strip to `0`. Only the full group id is unique, and it is stable only while the child order holds (see Risk R3). The stripped format id equals `stableSubtitleKey()` for side-loads and the rendition id for manifest tracks, and it survives a rebuild that reorders children. That is why it goes first. Coordinates are the positional fallback the first batch removed (`PlaybackController.kt:902-904`). **This changes AC-4's wording; see Q1.** |
| D9 | Off detection in the controller | `selectSubtitle` (`PlaybackController.kt:549-563`) disables text on `track == null \|\| track.isOff`, replacing `track.url.isEmpty()` (`:551`). | A manifest-only row that matched no API row has `url == ""`. Under today's check it would turn subtitles **off** when picked. |
| D10 | Labeler reconciliation (owner Q2) | Port upstream `SubtitleLabeler` into `ui/feature/player/model/SubtitleLabeler.kt`, with the constructor taking `displayLocale: Locale` in place of a language-tag string. Move `canonicalSubtitleLanguage` (with the fork's bibliographic → terminology map, `model/SubtitleLabels.kt:42-61`), `subtitleLanguageDisplayName` and `iso3Locales` (`:63-90`, the Russian ISO-3 rule) into `model/SubtitleLanguage.kt`, and add upstream's `sameSubtitleLanguage` and `AI_GENERATED_SUBTITLE_LANGUAGE`. Add a top-level `subtitleLabeler(resources: ResourceProvider)` that wires the first batch's strings (`player_subtitle_ai_generated`, `_forced`, `_variant_label`, `_unknown`: `app/src/main/res/values/player.xml:44-47`, `values-en/strings.xml:246-249`) and `resources.locale` (`core/system/ResourceProvider.kt:7-8`). Delete `subtitleLabels()` (`model/SubtitleLabels.kt:17-40`) and then the file. `PlayerUIMapper.mapSubtitleTracks` (`model/PlayerUIMapper.kt:35-66`) builds API rows (with `isForced`, `sourceFile`) and labels them through the labeler. | For API rows (no `descriptiveLabel`) upstream's `baseLabel` reduces to the first batch's chain: language name, then raw upper-case code, then "Subtitles N"; ordinals on collisions; forced qualifier. API-row labels stay byte-identical. Manifest rows additionally get upstream's readable-label rules. The package stays `model`, where the mapper (its first user) lives; the VM builds its own labeler from `resources` because `PlayerVMTestFixture` mocks the mapper (`app/src/test/.../vm/PlayerVMTestFixture.kt:86`). |
| D11 | VM: rows and selection on each report | `PlayerVM` keeps `apiSubtitleTracks`, taken from the published content state in `publishPreparedContent` (`PlayerVM.kt:362-368`) and recomputed with `mapper.mapSubtitleTracks` when `refreshStreamLinksOnce` swaps subtitles (`:469-473`). In `onTracksUpdated` (`:211-222`): rows = `subtitleTracks?.let { merger.merge(apiSubtitleTracks, it) } ?: apiSubtitleTracks`. Audio rows and index are kept when `audioTracks` is empty. The previously selected row is re-found in the new rows by a new resolver method, `AudioTrackPreferenceResolver.findSameSubtitleTrackIndex(tracks, previous)`: Off goes to Off; otherwise source identity, then full group id + track index, then unambiguous canonical language. When nothing matches, the result is Off. Then: if `!tracksRestoredForCurrentMedia`, the existing restore runs unchanged (D12). Otherwise, when the re-found row differs from the previous one, the VM calls `playbackController.selectSubtitle(row)` with no persist. | AC-6. The re-send keeps the controller's pending row current after `switchStream` or a refresh. It stops after one round: the next report re-finds an equal row, and the player ignores parameters that have not changed. A merge that drops the picked row ends at Off **and** disables text, so the picker never says Off while text plays. |
| D12 | Saved-preference restore (AC-7) and D3 | `restoreTrackPreferences` (`PlayerVM.kt:526-554`) is unchanged. It still runs once per media on the first report, and still falls back to re-applying the current row with `persist=false` (`:547-553`). It now runs over merged rows. One change in `findSubtitleTrackIndex` (`vm/AudioTrackPreferenceResolver.kt:24-37`): the unambiguous-language rule (`:92-99`) compares with `sameSubtitleLanguage` and no longer uses `==`. Exact URL and stable key are unchanged. | Rows linked to API rows (side-loads and identity-matched manifest rows) carry the API `url`, so a saved URL still resolves. Manifest rows may say `ru` where the saved API language is `rus`, so the language rule needs the canonical comparison. The first report already contains the text groups: `MergingMediaPeriod` reports prepared only after every child has, and lazy side-load children prepare without I/O (`ProgressiveMediaPeriod.java:286-300`). So the restore never runs before the text rows exist, and upstream's "retry until discovered" (`b755826c:.../PlayerVM.kt` `restoreSubtitlePreference`) is not needed in this step. |
| D13 | Warm-up player (AC-10) | Leave the warm-up shape alone: HLS still goes through `HlsMediaSource.Factory` directly (`PlaybackController.kt:758-774`), and progressive through `setMediaItem`. Only the item is now built by `PlaybackMediaItemFactory`. | The warm-up only has to put the playlist and first segments into the shared cache. `CacheDataSource` has no custom key factory (`PlaybackController.kt:653-656`), so keys are URIs. The main player's HLS child requests the same URIs through the same `CacheDataSource.Factory` (`:403-412`), now just inside a `MergingMediaSource`, so the warm cache is still hit. Lazy side-load children add no I/O before preparation (`ProgressiveMediaPeriod.java:286-300`), so time to first frame does not change. AC-10 says "device observation only if warm-up shape changes": it does not, but the main path's shape does, so the device pass should note start time anyway. |
| D14 | Do unselected side-loads download? | No; no code is needed. | Side-loads are built with `enableLazyLoadingWithSingleTrack` (`DefaultMediaSourceFactory.java:627-640`). `ProgressiveMediaPeriod.continueLoading` returns false while `singleTrackFormat != null && enabledTrackCount == 0` (`ProgressiveMediaPeriod.java:528-533`), and text is disabled at build (`PlaybackController.kt:393`). The same holds for HLS manifest renditions (unselected wrappers do not load). |
| D15 | Is a failing side-loaded subtitle (404/TLS) fatal (AC-9)? | No. It is not fatal under the current `PlaybackErrorPolicy`, so the policy is not changed. Media3 drops the text track. | 404 and TLS errors are `IOException`s, so the policy retries them (`vm/PlaybackErrorPolicy.kt:30-36`). After `MINIMUM_RETRY_COUNT` (`vm/PlaybackNetworkTuning.kt:24`) the sample stream throws. A parse error stops at once (`DONT_RETRY_FATAL`, `ProgressiveMediaPeriod.java:862-897`). `TextRenderer.isReady()` then turns false (`TextRenderer.java:532-552`), and `ExoPlayerImplInternal` calls `maybeThrowRendererStreamError` (`ExoPlayerImplInternal.java:1495-1503`). For `TRACK_TYPE_TEXT` that **disables the renderer and logs**; it does not throw (`:3845-3880`). The error never reaches `onPlayerError`, so `streamRecovery()` (`PlaybackController.kt:320-358`) never starts a CDN fallback. Before selection nothing loads (D14), so `maybeThrowPrepareError` (`MergingMediaPeriod.java:96-100`) has nothing to throw. One side effect is in Risk R2. |
| D16 | DI | None. `PlaybackMediaItemFactory`, `SubtitleTrackSelector` (both in the controller), `SubtitleTrackMerger` and `SubtitleLabeler` (in the VM) are plain fields, like `AudioTrackPreferenceResolver` at `PlayerVM.kt:175`. The screen module (`ui/feature/player/component/PlayerScreen.kt:47-56`) is unchanged. | Stateless helpers do not need Koin. Scope lifetime is unchanged. |

#### How the acceptance criteria are met

- AC-1 / AC-2: D1 + D4 + D5 + D7 (HLS rows = manifest renditions + side-loads, merged).
- AC-3: D7, `SubtitleTrackMergerTest`.
- AC-4: D8, `SubtitleTrackSelectorTest`. The wording needs changing (Q1).
- AC-5: D2 + D4 (progressive keeps API rows, side-loaded, Off at build `PlaybackController.kt:393`).
- AC-6: D11 (VM tests drive `onTracksUpdated` with player rows).
- AC-7: D3 + D12 (extend `PlayerVMTrackSelectionTest`).
- AC-8: D10 (`SubtitleLabelerTest`, which takes over `app/src/test/.../model/SubtitleLabelsTest.kt`).
- AC-9: D15 (by Media3 design). The evidence method is in question (Q2).
- AC-10: D13.
- AC-11: JUnit 5, `FakeResourceProvider`. There is no Robolectric in this project, so `PlaybackMediaItemFactory` should expose the subtitle spec (mime, language, flags, id) as a pure function and be tested through it. `MediaItem.Builder`/`toUri()` need `android.net.Uri`, which the JVM stubs do not provide.

#### Interaction with the deferred restore issue (subtitle-restore-model)

Part of the deferred issue goes away with D4; this cannot be avoided. Text tracks are reported even when a stream has no audio group, so `onTracksUpdated` fires, the D3 re-apply runs and the current row is sent again. That happens whenever the stream has any text track, which is always the case once the API lists a subtitle, because it is side-loaded. The other half is unchanged: `prepare()` still clears `pendingSubtitleTrack` (`PlaybackController.kt:371-374`). A rebuild of the same media (buffer preset `PlayerVM.kt:862-873`, Fast DNS `:875-884`) still relies on the VM's first report after the rebuild: D11's re-find, then the D12 restore, then the D3 re-apply. Only a stream with neither audio nor text stays unrestored, and it has nothing to restore. subtitle-restore-model should re-test this, close the audio-less part, and keep "restore across recreation" (VM re-creation, new pref keys) as its scope.

#### Effect on playback-controller-split

That run's SOLID-4 extraction of text-track matching is done here (`SubtitleTrackSelector`, pure, unit-tested: identity first, no positional fallback). The FORCED flag moves into `PlaybackMediaItemFactory`, which is unit-testable. Still untested and left to that run, or to P6 instrumented tests: the text-off default at build (`PlaybackController.kt:393`) and the parameter building in `selectSubtitle` / `applySubtitleTrackSelection`. Both need a real `ExoPlayer`. That run keeps its debug-info extraction and package move (STR-3), plus Q3 below.

### Changes by layer

#### API / repository

- `data/api/models/Models.kt:429-435`: `SubtitleLink` gets `file: String? = null` (D3). `PlayerPreferencesRepository` is unchanged (lang + URL keys only; new keys belong to subtitle-restore-model).

#### ViewModel state and actions

- `ui/feature/player/model/PlayerUIModels.kt:26-32`: `SubtitleTrackUIState` gets the new fields and loses `index`; add `isOff` (D5, D6).
- `ui/feature/player/vm/SubtitleTrackIdentity.kt:1-12`: replace with upstream's full file (`b755826c:.../vm/SubtitleTrackIdentity.kt`: `sameSubtitleIdentity`, `SUBTITLE_FILE_EXTENSION`, `<file>/index.m3u8` normalisation). Add the shared `String.withoutMergedSourcePrefix()`. `stableSubtitleKey()` gives the same result for API URLs: the new suffix rule only touches `/index.m3u8` paths.
- New `ui/feature/player/vm/PlaybackMediaItemFactory.kt` (D1, D2). It takes `StreamCandidate` (`PlayerInteractor.kt:23-26`), not upstream's `StreamSource`.
- New `ui/feature/player/vm/SubtitleTrackMerger.kt` (D7) and `SubtitleTrackSelector.kt` (D8).
- `ui/feature/player/vm/PlaybackController.kt`:
  - `:67`: the callback signature (D4).
  - `:424, :492, :606-635`: the item now comes from the factory; `subtitleMimeType` moves into it.
  - `:659-681`: `setMediaSource` becomes `setMediaItem` only (D1).
  - `:480`: comment.
  - Remember the current `StreamType` next to `currentStreamUrl` (`:162`, set at `:425, :493`, cleared at `:576`).
  - `:551`: `isOff` (D9).
  - `:852-868`: guard plus text rows, using `player.currentManifest as? HlsManifest` for renditions (D4, D5). The merged timeline is child 0's, so the HLS manifest is still exposed (`MergingMediaSource.java:309-314`).
  - `:874-940`: the selector (D8).
  - Warm-up `:689-774`: shape unchanged (D13).
- `ui/feature/player/vm/AudioTrackPreferenceResolver.kt`: `:92-99` canonical language (D12); add `findSameSubtitleTrackIndex` (D11).
- `ui/feature/player/vm/PlayerVM.kt`:
  - `:211-222`: `onTracksUpdated` merges and re-finds the selection (D11).
  - `:362-368`: capture `apiSubtitleTracks`.
  - `:469-473`, `:487`: refresh recomputes them.
  - Add fields `subtitleTrackMerger` (built from `subtitleLabeler(resources)`) and `apiSubtitleTracks`.
  - `:526-554` and `:807-817` unchanged.
- `ui/feature/player/model/PlayerUIMapper.kt:35-66`: API rows through the labeler (D10).
- New `ui/feature/player/model/SubtitleLabeler.kt` and `SubtitleLanguage.kt`; delete `model/SubtitleLabels.kt` (D10).

#### Screen and DI module

- None (D16). `ui/feature/player/component/PlayerScreen.kt:47-56` unchanged.

#### Compose content

- No layout change. `component/PlayerSettingsPanel.kt:230-253` still renders `label` and sends `SelectSubtitle(index)`.
- `component/PlayerPreview.kt:36-42`: the preview rows are positional `SubtitleTrackUIState(0, "…", …)` and must switch to named arguments without `index` (compile fix only).

#### Resources

- None new. Reuses `player_subtitles_off` and `player_subtitle_*` (`app/src/main/res/values/player.xml:44-47`, `values-en/strings.xml:245-249`).

#### Tests (for test-designer; JUnit 5)

- New: `SubtitleTrackMergerTest`, `SubtitleTrackSelectorTest`, `SubtitleLabelerTest` (absorbs `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/SubtitleLabelsTest.kt`), a pure `PlaybackMediaItemFactory` spec test, and resolver tests for `findSameSubtitleTrackIndex`.
- Extend `PlayerVMTrackSelectionTest` (AC-6/7).
- Adapt `PlayerUIMapperTest` and `PlayerVMTestFixture.kt:229-238` (no `index`).
- `PlayerVMTestFixture.kt:86` mocks the mapper with `relaxUnitFun`, so a VM test that reaches `refreshStreamLinksOnce` must stub `mapSubtitleTracks`.

### Alternatives rejected

- Hand-built `MergingMediaSource` + `SingleSampleMediaSource` around HLS (upstream `003d4c55`): upstream itself replaced it with `DefaultMediaSourceFactory` in `c42dd8c1`, and it duplicates what the factory already does.
- Applying `shouldSideLoad` (`embed`) filtering: it changes progressive rows (AC-5).
- Merging on progressive too (upstream): container text tracks appear and same-language rows regroup on progressive (AC-5).
- Selecting by the prefix-stripped group id, as the brief words it: the id is not unique across side-loads (all strip to `0`; `ProgressiveMediaPeriod.java:1060`).
- Upstream's `matchByCoordinates` and the resolver's `allowPositionFallback`: these are a positional rule the first batch removed for picking the wrong subtitle.
- Upstream's "Off-only picker until tracks are discovered": not needed because the first report already carries text groups (D12), and API rows before that keep progressive unchanged.
- Changing `PlaybackErrorPolicy` to fail subtitle loads faster: no AC needs it, and it would also change video-load retries.
- Making the warm-up side-load or go through `DefaultMediaSourceFactory`: no cache benefit; it would only add a subtitle child.

### Risks (highest first)

1. **R1 Focus / D-pad: rows change under an open Subtitles page.** The first HLS report, a refresh or an HLS↔progressive fallback replaces `subtitleTracks` while `SubtitlesPage` may be open. It renders with `forEachIndexed` without keys and a single `focusRequester` on `focusIndex` (`component/PlayerSettingsPanel.kt:236-245`). If the focused row disappears or shifts, focus can drop out of the panel. Window: from content publish (`PlayerVM.kt:352`) to the first `onTracksChanged` (`PlaybackController.kt:234-237`). Rows that compare equal do not re-emit. Device check: open Subtitles during startup and during a quality switch.
2. **R2 Video loading paused while a failed subtitle is still "ready".** `isLoadingPossible` stops loading **the whole merged period** while any of its sample streams reports a loading error (`ExoPlayerImplInternal.java:3568-3572`, `MediaPeriodHolder.java:403-418`). The text renderer is dropped only once `TextRenderer.isReady()` turns false. If part of the file loaded and later cues are buffered, `isReady()` stays true until they run out (`TextRenderer.java:539-544`), and the video buffer can drain meanwhile. This needs a partial-then-failed subtitle download; full 404/TLS failures drop the track within the retry ladder (`PlaybackNetworkTuning.kt:16-31`). Not fatal; at worst a rebuffer. Lives in Media3; no fork code to change. Watch for it in the device pass.
3. **R3 Merge prefix and child order across rebuilds.** Child order = API list order (`DefaultMediaSourceFactory.java:606-659`). `switchStream` (`PlayerVM.kt:425`) and CDN fallback (`:445`) pass the same `media.subtitles`, so the ids stay the same. `refreshStreamLinksOnce` (`:496`) passes a refreshed list, which may be reordered. An HLS↔progressive fallback removes the manifest child entirely. Mitigation: the selector tries source identity before the full group id (D8), the VM re-finds by identity (D11), and a pick that cannot be re-found goes to Off and disables text.
4. **R4 Manifest↔API linking depends on `file`.** If KinoPub omits `file` or uses a different path, manifest rows keep `url == ""`. Restore by URL then falls back to unambiguous language (D12), and two same-language manifest variants restore to Off under D3. Lives in `SubtitleTrackMerger.enrichPlayerTracks` and `AudioTrackPreferenceResolver`. Device pass: check one HLS title where both sources offer a language.
5. **R5 Callback re-entrancy.** The VM's re-send (D11) runs inside `onTracksChanged` (`PlaybackController.kt:234-237`) before `applyPendingSubtitleSelection`, so it changes `trackSelectionParameters` from within a listener. Media3 allows this. It ends after one extra report only because rows are compared by value: a labeler or merger that is not deterministic would loop. The tests must assert a single re-send.
6. **R6 Data shape.** `SubtitleTrackUIState` loses `index` and gains nine fields (`@Immutable` data class, still stable). `SubtitleLink.file` is additive. No persisted shape changes in this run.
7. **R7 Main-thread work.** A merge, labelling and re-find run on every `onTracksChanged` on the main thread. With n of about 10 the cost is negligible. `iso3Locales` is computed once and lazily (already the case since the first batch).
8. **R8 Network / cache.** On HLS, subtitle files now enter the shared media cache when selected (as on progressive today). They are small and evicted by LRU. Nothing is fetched while unselected (D14).
9. **R9 Debug "stream source" line.** Side-load loads report `TRACK_TYPE_UNKNOWN` (`ProgressiveMediaPeriod.java:786-787`), so the `TRACK_TYPE_TEXT` filter in `updateEffectiveStreamSource` (`PlaybackController.kt:816-821`) does not catch them. A selected HLS side-load on another host can show its host there. This already happens on progressive. See Q3.
10. **R10 Rollout / baseline profile.** No flag: HLS pickers change for every user on update. The new classes sit on the player start path; the baseline profile is regenerated at release (`tools/generate-baseline-profile.sh`).

### Open questions

1. **Q1 (AC-4 wording; blocks: yes, the spec states the AC).** AC-4 says "selected by the prefix-stripped, merge-stable group id". The prefix-stripped group id is the same (`0`) for every side-loaded subtitle (`ProgressiveMediaPeriod.java:1060`, `MergingMediaPeriod.java:327-328`), so an implementation that follows the wording literally cannot tell side-loads apart. Recommended rewording: "…Then the text track is selected by its prefix-stripped source identity (format id / stable subtitle key), falling back to the exact merged `TrackGroup.id` + track index and then to an unambiguous language; never by position. (unit: `SubtitleTrackSelectorTest`)".
2. **Q2 (AC-9 evidence; blocks: no for design, needs the owner at gate 1).** A unit test with a fake source needs a real `ExoPlayer` on the JVM (Robolectric, which the project does not have). A device check needs a title whose subtitle URL fails, which we cannot arrange. Recommended: AC-9 is met by the Media3 1.11.0 path in D15, recorded in the spec; a hermetic test follows in upstream-sync-triage P6 (`PlaybackControllerNetworkTracksTest`); and a device check is run if such a title turns up.
3. **Q3 (R9; blocks: no).** Should side-loaded subtitle loads be kept out of the debug "stream source" host? Recommended: not in this run. Hand it to playback-controller-split, which owns debug-info extraction, as a filter on the side-loaded URIs.
4. **Q4 (scope note; blocks: no).** Upstream also lists container (embedded) text tracks on progressive (`c798fdcb`, `5a43798a`). This design keeps progressive API-only to hold AC-5. Recommended: leave it out; reopen only if the owner wants embedded tracks on progressive (that would amend AC-5).


### Design delta 2 (fix round 2, architect: design-architect-2.md)

Scope: QA round 2 Defect 1, review-edge-2 F1 (major), review-codex-2 (minor, same issue) and review-tests-2 F1.
Worktree HEAD `1b79c661`. App paths are relative to `app/src/main/java/com/kino/puber/` and test paths to
`app/src/test/kotlin/com/kino/puber/` unless they start with `app/`. Every `path:line` below was read at that HEAD. Media3
citations are from the `1.11.0` tag (`libraries/exoplayer/...`, `libraries/exoplayer_hls/...`).

This delta replaces fix `36da8957`, which treats every text-less report as transient. It amends spec D11 and adds
D17-D20. The ACs are unchanged.

### 1. Where Outlander's subtitles come from

**Finding: on the manual-quality stream the item had no side-loads. Outlander's `Russian`/`English` rows come from
the `hls4` manifest only, so the 720p/480p `url.hls` stream really has no subtitles.** Confidence: high (about 90 %).
The deduction is from code plus the round-1 and round-2 device observations. The raw API list was not inspected.

How the deduction goes:

1. All three paths that rebuild the item pass the API subtitles and go through the factory: quality switch
   (`ui/feature/player/vm/PlayerVM.kt:419-427`), next-candidate fallback (`:440-447`) and link refresh (`:497`, with
   `refreshedMedia.subtitles` from `:469-473`). Each one calls `PlaybackController.switchStream`
   (`vm/PlaybackController.kt:501-521`), which builds the item with `mediaItemFactory.build(stream, subtitles)` (`:507`).
   `PlaybackMediaItemFactory.spec` attaches every link that has a url (`vm/PlaybackMediaItemFactory.kt:38-52`, filter
   `:78-79`). No path drops them, so no switch path has a bug.
2. When an item has subtitle configurations, `DefaultMediaSourceFactory` wraps it in a `MergingMediaSource`
   (`DefaultMediaSourceFactory.java:603-659`). `MergingMediaPeriod.onPrepared` publishes its track groups only after
   **every** child has prepared, and then publishes all of them at once (`MergingMediaPeriod.java:300-335`). The lazy
   side-load children prepare without I/O. So the first prepared `Tracks` of a side-loaded item always contains the
   side-load text groups.
3. Before that point the player masks the new item with an empty track set. `setMediaItem` changes the playing
   period, so `trackGroups = TrackGroupArray.EMPTY` (`ExoPlayerImpl.java:2823-2824`). The controller's guard
   (`PlaybackController.kt:822`) suppresses that report, because it has neither audio nor text groups.
4. HLS subtitle renditions are also known at preparation. `HlsMediaPeriod.buildAndPrepareSampleStreamWrappers` always
   builds them from the multivariant playlist, not only under chunkless preparation (`HlsMediaPeriod.java:559-560`,
   `:820-873` `prepareWithMultivariantPlaylistInfo`).
5. So **a report with audio groups and no text groups can happen only for an HLS item that has neither side-loads
   nor subtitle renditions**. The "transient" state that `36da8957` assumed does not exist in Media3 1.11.0.
6. The devices fit this exactly:
   - Round 1, 720p: the picker collapsed to `Off` and stayed there for 5-10 s. If side-loads had existed, their
     groups would have rebuilt the rows.
   - Round 2: re-picking `Russian` at 480p rendered nothing (0/27). The controller found no text group, so it set no
     override and only a preferred language (`PlaybackController.kt:849-867`).
   - Audio reads `Track 1` on `url.hls`. The label falls back from name and language to the index
     (`PlaybackController.kt:826`), so this playlist has no `EXT-X-MEDIA` metadata at all, subtitles included.

Consequences outside this fix:

- The evidence file lists Outlander as **H2** ("API lists Russian, English"). That was inferred, and is most likely
  wrong. Outlander is the **H1 manifest-only** case, so it is evidence for AC-2 (manifest row listed and renders),
  not for AC-1 / D-1 (side-load renders on HLS). AC-1 on a device is still open.
- Manual quality works as a discriminator on the device: rows that survive on `url.hls` are side-loads. QA can use
  it on Psyren S1E1 (9 API languages) for AC-1.
- Residual uncertainty: an API list whose every link has a blank `url` gives the same picture. The outcome is the
  same: no side-loads. QA can confirm with one count of `subtitles` for Outlander S1E5, without keeping the response.

Nothing in the side-load path is broken. The defect is the VM rule.

### 2. Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D17 | What does a report without text groups mean? | It is **definitive**: the stream has no subtitles. The VM no longer has any notion of a "transient" report. Remove `textlessReport` (`PlayerVM.kt:534-541`). | §1 points 2-5. Holding rows is what produced "`Russian` checked, nothing renders" (Defect 1, edge F1, codex). |
| D18 | Where is "not known yet" decided? | In the controller, through a pure function `subtitleReportKind(streamType, hasSideLoads, textGroupCount)` in `vm/PlayerTextTracks.kt`. It returns `API_ROWS` (progressive or unknown type; the callback gets `null`, as today `PlaybackController.kt:838-841`), `NOT_YET` (HLS and `hasSideLoads` and `textGroupCount == 0`: `notifyTracksUpdated` **returns without calling back**), or `PLAYER_ROWS` (otherwise, including an empty list). It is called in `notifyTracksUpdated` (`:817-843`) after the existing both-empty guard (`:822`). | Per §1 a side-loaded item cannot report audio without text, so `NOT_YET` is a defensive guard that is normally unreachable. If it ever fires, the next report carries everything. Being pure, it is unit-tested without ExoPlayer, and the controller's own logic becomes one call. |
| D19 | Where does the viewer's pick live? | **VM owns the intent**: a new `private var intendedSubtitleRow: SubtitleTrackUIState?` (null = Off) next to `apiSubtitleTracks` (`PlayerVM.kt:179`). It is not view state and is never persisted by itself. **The controller's `pendingSubtitleTrack` (`PlaybackController.kt:170`) only mirrors the row the picker shows.** It is set by `selectSubtitle` (`:567-581`), cleared by Off and by `prepare` (`:387`), and its semantics do not change. | One owner. The controller cannot tell "not on this stream" apart from "gone". The VM sees API rows, merges and media changes. A mirror that always equals the displayed row keeps D11's guarantee: the picker never says Off while text plays. |
| D20 | Single source for "has side-loads" (tests-2 F1) | Add `val hasSideLoads: Boolean get() = subtitles.isNotEmpty()` to `MediaItemSpec` (`vm/PlaybackMediaItemFactory.kt:21-26`). Change `build` to `fun build(spec: MediaItemSpec): MediaItem` (`:54-55`). In `prepare` (`PlaybackController.kt:436-440`) and `switchStream` (`:507-511`): `val spec = mediaItemFactory.spec(stream, subtitles)`, `setMediaItem(mediaItemFactory.build(spec)[, savedPosition])`, `currentItemHasSideLoads = spec.hasSideLoads`. The warm-up (`:701`) uses `build(spec(stream, subtitles))`. Remove the two `subtitles.sideLoadable().isNotEmpty()` expressions. | The flag comes from the spec that was actually attached, and the existing spec test covers it. |
| D11 (amended) | VM rule per report | Replace the D11 text with the rule in §3. The new part: a pick that cannot be re-found shows Off and sends Off once, but the **intent is kept** (not persisted) and re-applied when a later report contains the row again. | Edge F1 fix direction, without depending on a controller pending pick surviving across streams. |

### 3. The exact VM rule (`PlayerVM.onTracksUpdated`, replaces `PlayerVM.kt:527-567`)

New state, next to `tracksRestoredForCurrentMedia` (`PlayerVM.kt:163`):

- `intendedSubtitleRow: SubtitleTrackUIState? = null`.
- `subtitleRestoredForCurrentMedia = false`. From now on `tracksRestoredForCurrentMedia` covers **audio only**.
  - Both flags are reset together at `:917` (buffer preset rebuild), `:928` (Fast DNS rebuild) and `:972`
    (`resetMediaSession`).
  - `intendedSubtitleRow = null` **only** in `resetMediaSession` (`:957-974`, reached by episode switch `:981` and
    retry `:1391`). A rebuild of the same media keeps the intent.

On each report `(audio, audioIndex, reported)`:

1. `previousRow` = the displayed row (as today, `:532-533`).
2. `rows = reported?.let(::mergeSubtitleTracks) ?: apiSubtitleTracks`. A report with no text groups merges to
   `[Off]` (`vm/SubtitleTrackMerger.kt:23-25`). There is no special case for it.
3. `offIndex = rows.indexOfFirst { it.isOff }.coerceAtLeast(0)`.
   `shown = intendedSubtitleRow?.let { resolver.findSameSubtitleTrackIndex(rows, it) } ?: offIndex`.
   `findSameSubtitleTrackIndex` returns the Off index when nothing matches (`vm/AudioTrackPreferenceResolver.kt:48-65`).
4. If `shown != offIndex`, set `intendedSubtitleRow = rows[shown]`. This refreshes the identity, for example to
   refreshed URLs. When nothing matches, the intent stays as it was.
5. `updateContent`: audio exactly as today (`:547-552`), `subtitleTracks = rows`, `selectedSubtitleIndex = shown`.
6. If `!tracksRestoredForCurrentMedia`: set the flag, then run the **audio** half of `restoreTrackPreferences`
   (`:573-586`).
7. If `!subtitleRestoredForCurrentMedia`:
   - If `rows.none { !it.isOff }`, **return**. The restore stays pending, nothing is sent, and after `prepare` text is
     already disabled (`PlaybackController.kt:406`, `:387`).
   - Otherwise set the flag and run the **subtitle** half (`:588-599`, unchanged: the saved URL, then the stable key,
     then an unambiguous language; else re-send the displayed row; `persist = false`). Then return.
8. Otherwise `row = rows.getOrNull(shown)`. If `row != null && previousRow != null && row != previousRow`, call
   `playbackController.selectSubtitle(row)`. This is the existing re-send (`:562-566`), without persist and without
   touching the intent. **Only rows from the current `rows` are ever sent.**

Changes elsewhere in `PlayerVM`:

- `applySubtitleSelection` (`:853-863`) sets `intendedSubtitleRow = subtitle.takeUnless { it.isOff }`. When
  `persist` is true (an explicit viewer pick), it also sets `subtitleRestoredForCurrentMedia = true`, because an
  explicit choice supersedes a pending restore.
- `saveTrackPreferences` (`:1826-1837`) takes its subtitle part from the intent, not from the displayed index:
  - If `!subtitleRestoredForCurrentMedia`, it passes through `interactor.getPreferredSubtitleLang/Url(params.itemId)`
    unchanged.
  - Otherwise it saves `intendedSubtitleRow` (null means Off, which writes nulls exactly as today).
  - This is what stops an audio pick (`:843-851`) from persisting Off while the subtitles are collapsed or the
    restore is pending. The repository removes keys on null (`data/repository/PlayerPreferencesRepository.kt:28-56`).

How each case comes out:

| Case | Result |
|------|--------|
| Auto → 720p, Outlander | rows `[Off]`, Off shown, one `selectSubtitle(Off)`, nothing persisted, intent = `Russian`. Further text-less reports: no send. |
| 720p → Auto | rows rebuilt, intent re-found, `Russian` shown, one `selectSubtitle(Russian)`, cues return without a re-pick. |
| Stream has other text but not the pick | Off shown, Off sent (D11: no language fallback under an Off picker), intent kept and re-applied if the row returns. |
| Next episode, first report text-less | Audio restored. Subtitle restore pending, Off shown, nothing sent. An audio pick keeps the saved subtitle. The first report with text runs the restore. |
| Link refresh | `apiSubtitleTracks` recomputed (`:488`). The next report merges fresh rows and the intent is re-found by identity (`sameSubtitleIdentity`, `vm/SubtitleTrackIdentity.kt:13-23`: path, suffix, stable key). Only the fresh row is sent. A text-less report after a refresh shows `[Off]`, so stale rows are never shown or sent. |
| Rebuild (buffer, DNS) | Intent kept. The restore runs on the first report with text, as before (`prepare` cleared the controller mirror). |
| Viewer picks Off while collapsed | Intent null, Off persisted (explicit), restore marked done. Auto later stays Off. |
| Progressive | `reported == null` keeps the API rows (AC-5). Re-finding by intent equals re-finding by the displayed row. |

### 4. Changes by layer

#### API / repository

- None.

#### ViewModel state and actions

- `vm/PlaybackMediaItemFactory.kt:21-26, 54-55`: `MediaItemSpec.hasSideLoads`; `build(spec)` (D20).
- `vm/PlayerTextTracks.kt`: add `enum class SubtitleReportKind { API_ROWS, NOT_YET, PLAYER_ROWS }` and the pure
  `subtitleReportKind(...)` (D18).
- `vm/PlaybackController.kt`:
  - `:436-440` and `:507-511`: the spec drives the item and the flag (D20). `:701`: the warm-up uses
    `build(spec(...))`.
  - `:834-842`: `when (subtitleReportKind(currentStreamType, currentItemHasSideLoads, textGroups.size))`. `NOT_YET`
    returns; `API_ROWS` sends `null`; `PLAYER_ROWS` sends `buildSubtitleRows(...)` as today.
- `vm/PlayerVM.kt`:
  - `:163`: add `subtitleRestoredForCurrentMedia`. `:179`: add `intendedSubtitleRow`.
  - `:527-567`: the rule in §3. `:572-600`: split into `restoreAudioPreference()` and
    `restoreSubtitlePreference()`; the bodies are unchanged.
  - `:853-863`: set the intent and the explicit-pick flag.
  - `:917, :928, :972`: reset the new flag. `:957-974`: clear the intent.
  - `:1826-1837`: the subtitle part comes from the intent, or passes through while the restore is pending.
- `AudioTrackPreferenceResolver`, `SubtitleTrackMerger`, `SubtitleTrackSelector`: unchanged.

#### Screen and DI module

- None.

#### Compose content

- None. `component/PlayerSettingsPanel.kt:230-253` already clamps `focusIndex` with `coerceToIndex` and handles a
  one-row list.

#### Resources

- None.

### 5. Unit tests (JUnit 5, `PlayerVMTestFixture`)

`ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`:

- **Replace** `tracksUpdated_textlessReportAfterQualitySwitch_keepsRowsAndPick` (`:345-368`) with
  `tracksUpdated_textlessReport_collapsesToOff_sendsOffOnce_doesNotPersist`.
  - Steps: pick `rus.vtt`, `clearMocks`, then `report(emptyList())` twice.
  - Assert: rows `== [Off]`, index 0, exactly one `selectSubtitle(match { it.isOff })`, zero `saveTrackPreferences`.
  - Covers AC-6, edge F1 (a), codex.
- `tracksUpdated_textlessThenRowsReturn_reappliesIntendedRow`.
  - Steps: continue the case above with `report(rows)`.
  - Assert: the `rus.vtt` row is shown; exactly one `selectSubtitle(match { it.playerTrackId == "rus.vtt" })`; zero
    `saveTrackPreferences`.
  - Covers AC-6 and the Defect 1 return to Auto.
- Extend `tracksUpdated_selectedRowDisappears_goesOffAndDisablesText` (`:331-343`). Assert unchanged, then
  `report(original rows)` re-selects the Russian row exactly once (D11 amended).
- `selectAudio_whileSubtitlesCollapsed_persistsIntendedSubtitle`.
  - Steps: after the collapse, `SelectAudioTrack(1)`.
  - Assert: `saveTrackPreferences(42, any(), any(), "rus", <rus url>)`.
- `selectOff_whileCollapsed_forgetsIntent`.
  - Steps: collapse, `SelectSubtitle(0)`, `report(rows)`.
  - Assert: Off stays shown; no `selectSubtitle` of the Russian row after the Off pick; Off persisted.
- `nextMedia_textlessFirstReport_defersSubtitleRestore_keepsAudioRestore`.
  - Steps: saved `rus` and URL; `SelectEpisode(1, 2)`; `report(emptyList())` with audio.
  - Assert: `selectAudioTrack` was called; zero `selectSubtitle` calls; index 0.
  - Then `report(rows)`: the saved row is selected once with no persist.
  - Covers AC-7 and edge F1 (b).
- `nextMedia_textlessFirstReport_audioPick_keepsSavedSubtitlePreference`.
  - Assert: `saveTrackPreferences(42, any(), any(), "rus", savedUrl)`.
  - Covers AC-7 (no Off persisted).
- `refreshStreamLinks_textlessThenRows_sendsRefreshedRowOnly`.
  - Steps: pick a side-load with the old url; refresh with a same-path, new-host url (as at `:519-547`);
    `report(emptyList())`; then `report(listOf(sideLoad("eng", newUrl, 1)))`.
  - Assert: exactly one `selectSubtitle(match { it.url == newUrl })` and none with the old url after the refresh.
  - Covers edge F1 (c) and AC-6.
- P1: `rebuild_textlessFirstReport_audioPick_doesNotPersistOff` (buffer preset rebuild, `:908-919`).
- Must stay green unchanged: `:41-205` (restore), `:370-413` (re-send counts, R5), `:282-289` (progressive, AC-5),
  `:498-517`.

`ui/feature/player/vm/PlayerTextTracksTest.kt`, new `subtitleReportKind_*`:

- `progressiveOrNullType_isApiRows`.
- `hlsWithSideLoads_noTextGroups_isNotYet`.
- `hlsWithoutSideLoads_noTextGroups_isPlayerRows` (a real text-less stream).
- `hlsWithTextGroups_isPlayerRows_withOrWithoutSideLoads`.

`ui/feature/player/vm/PlaybackMediaItemSpecTest.kt`:

- `hasSideLoads_isFalse_whenEveryLinkHasBlankUrl_orListIsNullOrEmpty`.
- `hasSideLoads_isTrue_whenAnyLinkHasUrl`.
- These close tests-2 F1.

Device (qa-device), revised checklist D-4 on Outlander S1E5:

1. On Auto, `Russian` is rendering. Switch to 720p: the root reads `Subtitles Off` and the page lists only `Off`
   (checked).
2. Change the audio track once.
3. Back on Auto: `Russian` is checked and cues render without a re-pick.
4. Back out and reopen: `Russian` is restored, which proves Off was never persisted.
5. Optionally, 720p on Psyren S1E1: rows that survive are side-loads, which is the AC-1 discriminator from §1.

### 6. Alternatives rejected

- Keeping rows on every text-less report (`36da8957`): there is no end condition, and the picker lies on a stream
  that really has no text.
- A timer or "wait for STATE_READY" window to decide transience: per §1 there is no transient to wait for, and a
  timer adds a lifecycle to cancel.
- Keeping the pick only in the controller's `pendingSubtitleTrack` and not sending Off (edge F1's sketch): it leaves
  text enabled with a preferred language on the next stream, and the controller cannot handle a refresh or a new
  media. It would also be a second owner of the selection.
- Passing `null` for "not yet": `null` already means progressive or API rows (`PlaybackController.kt:68-76`), and
  would show API rows on HLS.
- Persisting Off when the rows collapse: it would erase the saved choice on a manual-quality switch (AC-7 on the next
  open).

### 7. Risks (highest first)

1. **Saved subtitle overwritten by an unrelated save.** Any `saveTrackPreferences` caller now depends on the intent
   or pass-through rule (`PlayerVM.kt:1826-1837`). Today the only callers are `:849` and `:861`. A future caller that
   reads `selectedSubtitleIndex` directly would persist Off again. Tests: the audio-pick cases above.
2. **Focus while the rows collapse under an open Subtitles page** (R1). Rows go from 3 to 1 under the focus
   (`component/PlayerSettingsPanel.kt:236-245`). `coerceToIndex` clamps the index. Round-1 D-7 saw this exact
   collapse with focus kept. Re-check on the device during the 720p switch.
3. **Re-send loop (R5).** Step 4 rewrites the intent on each report. It is deterministic (merger and labeler are
   pure), so the second identical report re-finds an equal row and sends nothing. Covered by `:370-413` plus the
   once-only counts above.
4. **`NOT_YET` hides audio rows** if a future Media3 version reports a side-loaded period without its text groups.
   It lives in `subtitleReportKind`. The impact is a stale audio picker, not a playback failure. It is pinned by
   §1's Media3 citations; re-check on a Media3 upgrade.
5. **Re-find by language on return** may land on a different same-language variant when only one remains
   (`AudioTrackPreferenceResolver.kt:60`). This is accepted under the existing rule.
6. **Refresh window.** Between the refresh's state update (`PlayerVM.kt:489-496`) and the next report, the picker
   still shows the old rows. A pick there sends an old-url row, and the selector matches by stable identity. Low
   impact, and the next report corrects it.

### 8. Open questions

1. **Q5 (scope; blocks: no).** Manual quality plays `url.hls` first (`domain/interactor/player/PlayerInteractor.kt:238-246`).
   On KinoPub that stream carries no subtitle or audio metadata, so manifest-only subtitles and named audio are lost
   on every manual quality. Recommended: a separate run that plays `hls4` with a max-video-size constraint for manual
   quality. Not in this run's ACs.
2. **Q6 (evidence; blocks: no).** Re-label Outlander in the device evidence as the manifest-only title (AC-2 / D-2
   evidence) and keep AC-1 / D-1 open until a side-load is proven, for example by Psyren on 720p. Recommended: yes,
   at the next QA pass, with one count of the API `subtitles` for Outlander S1E5 and no raw response stored.
3. No AC change is needed. D11 in the spec is amended as above. Checklist D-4 in `test-design.md` changes wording
   ("collapses to Off on a stream without text, returns on Auto"). That belongs to the test designer.

## Test design

<!-- owner: test-designer (test-design.md) -->

Base `a97ee2b1`. Inputs: `brief.md` (AC-1..AC-11), `design-architect.md` (D1..D16, R1..R10, Q1..Q4). Test paths are
under `app/src/test/kotlin/com/kino/puber/` in the worktree. Levels: `unit` (JUnit 5, `MainDispatcherExtension`,
`FakeResourceProvider` for mapped strings), `device` (emulator, D-pad via `adb -s <serial> shell input keyevent`).
No instrumented level: the project has no instrumented `PlaybackController` tests (hermetic ones are
upstream-sync-triage P6). No Robolectric: `Uri`/`MediaItem.Builder` are not usable on the JVM, so Media3 types stay out
of unit tests. The controller (`PlaybackController`) logic that needs a real `ExoPlayer` is covered by the device
checklist, not by mocks.

Assumed answers: Q1 = architect's wording (identity, then full group id + index, then unambiguous language, never
position). Q2 = AC-9 by Media3 code trace (D15). D4 = merged rows on HLS only.

### Existing tests to reuse or adapt

- `ui/feature/player/vm/PlayerVMTestFixture.kt` (lines ~86 mapper mock, ~229-238 `testSubtitleTracks` uses `index =`).
  Adapt: drop `index`. A VM test that reaches `refreshStreamLinksOnce` must stub `mapper.mapSubtitleTracks`.
- `ui/feature/player/vm/PlayerVMTrackSelectionTest.kt` (AC-6/7 live here; the restore tests at lines 34-205 must stay green).
- `ui/feature/player/model/SubtitleLabelsTest.kt`: ported into `SubtitleLabelerTest` (the old file is deleted with
  `SubtitleLabels.kt`; every old assertion must survive as a case, with the same expected strings).
- `ui/feature/player/model/PlayerUIMapperTest.kt`: adapt (no `index`; API labels unchanged byte for byte).
- Upstream sources to port from `git show b755826c:app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/<F>.kt`:
  `SubtitleTrackMergerTest`, `SubtitleTrackSelectorTest`, `SubtitleLabelerTest`, `SubtitleTrackIdentityTest`,
  `PlayerVMSubtitleVariantTest` (see "Port notes" for what must change).
- Not ported (step 2 or out of scope): upstream `PlayerVMSubtitleVariantTest` cases about language-less/forced restore
  across recreation, "deferred until manifest tracks appear", "after eleven empty updates", `retryPlayback_restores...`.

### 1. Risk profile

Score = probability (1-3) x impact (1-3). Risk ids are the architect's R1..R10.

| Risk | P | I | Score | Test at the level where it fails |
|---|---|---|---|---|
| R-sel Selector picks wrong side-loaded track (all stripped group ids are "0"; positional or group-id-only match) | 3 | 3 | 9 | unit `SubtitleTrackSelectorTest` (identity beats group id; same-"0" groups; no coordinates) |
| R3 Child order / prefix changes on rebuild (refresh reorders, HLS to progressive fallback) | 3 | 3 | 9 | unit selector (reordered groups), resolver `findSameSubtitleTrackIndex`, VM re-find; device quality switch |
| R4 Manifest to API linking fails when `file` absent or path differs (url stays "") | 2 | 3 | 6 | unit merger (`file` vs `<file>/index.m3u8`, no `file`), resolver language fallback; device title where both offer a language |
| R-merge Wrong source per language / `fromManifest` miscount (manifest track with unmatched rendition counted as side-load) | 2 | 3 | 6 | unit `SubtitleTrackMergerTest` (D5 case is mandatory) |
| D9 Off detection: manifest-only row with `url == ""` turns subtitles off when picked | 3 | 3 | 9 | unit VM/controller-facing: `isOff` extension test + VM `selectSubtitle` delegates the row; device (AC-2 select) |
| R5 Re-send loop: VM re-sends current row inside `onTracksChanged`; must stop after one | 2 | 3 | 6 | unit VM (assert exactly one `selectSubtitle` after identical repeated reports) |
| R1 Focus drops from open Subtitles page when rows change | 2 | 3 | 6 | device (cannot be unit-tested: Compose focus); unit only asserts equal rows do not re-emit |
| AC-5 progressive regression (rows or side-load behaviour change) | 2 | 3 | 6 | unit VM (`subtitleTracks = null` keeps API rows); unit factory spec (progressive: no m3u8 mime, same subtitle specs); device |
| Restore (AC-7) regression: saved URL/lang no longer resolves over merged rows | 2 | 3 | 6 | unit `PlayerVMTrackSelectionTest` extension + resolver `sameSubtitleLanguage` |
| D1 HLS mime/side-load config wrong (API subtitles still dropped, or HLS guessed as progressive) | 2 | 3 | 6 | unit factory spec (pure); device AC-1 (only real proof) |
| R2 / AC-9 failing subtitle pauses loading or goes fatal | 1 | 3 | 3 | UNTESTABLE hermetically (Q2); Media3 trace D15; opportunistic device |
| Label regression for API rows (D10 relabel) | 2 | 2 | 4 | unit `SubtitleLabelerTest` (old cases ported), `PlayerUIMapperTest` |
| R6 Data-shape churn (`index` removed, `PlayerPreview` compile) | 2 | 1 | 2 | compile via `check.full`; accepted |
| R7 main-thread merge cost | 1 | 1 | 1 | accepted (n about 10), note only |
| R8 cache growth from selected side-loads | 1 | 1 | 1 | accepted |
| R9 debug "stream source" shows side-load host | 2 | 1 | 2 | accepted, deferred to playback-controller-split (Q3) |
| R10 baseline profile not regenerated | 1 | 1 | 1 | accepted, release procedure |
| D13/AC-10 TTFF or warm-cache regression | 1 | 2 | 2 | device observation only; accepted if the note is recorded |

Highest: 9 (selector, child order, D9). Every score of 6+ has a test at its failing level except R1 (device by nature)
and the AC-9 family (3, below threshold, Q2).

### 2. Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable` (new) | an API-only language that the player reports as a side-load (prefixed id `1:<key>`) is a picker row and `SelectSubtitle` delegates that row to the controller |
| AC-1 | unit | P0 | `PlaybackMediaItemSpecTest.hls_attachesEveryApiSubtitle_withStableKeyAsIdAndLabel` (new, pure spec) | every API subtitle becomes a configuration with id = label = `stableSubtitleKey()`, FORCED from `forced`, and `APPLICATION_M3U8` mime for `StreamType.HLS` (the input `DefaultMediaSourceFactory` needs) |
| AC-1 | device | P0 | checklist D-1 | the subtitle actually renders on HLS (only real proof of D1) |
| AC-2 | unit | P0 | `SubtitleTrackMergerTest.merge_carriesManifestOnlyLanguage_withLanguageNameLabel` and `merge_keepsSideLoadedTrack_whenItsLanguageIsMissingFromTheManifest` (port) | manifest-only row is listed with a language-name label, `url == ""`, not Off |
| AC-2 | unit | P0 | `SubtitleTrackUIStateTest.isOff_isFalse_forManifestRowWithEmptyUrl` (new) + `PlayerVMTrackSelectionTest.selectSubtitle_manifestOnlyRow_isDelegatedNotTreatedAsOff` | D9: an empty `url` no longer means Off |
| AC-2 | device | P0 | checklist D-2 | manifest-only row is selectable and renders |
| AC-3 | unit | P0 | `SubtitleTrackMergerTest`: `merge_usesLargerLanguageGroup_preferringHlsOnTies` (parameterised, port), `merge_choosesSourceIndependentlyForEachCanonicalLanguage`, `merge_keepsUnmatchedManifestAndSideLoadedTracks_whenLanguageIsMissing`, `merge_hidesSideLoadedCopies_whenManifestCoversTheirLanguages` | one source per language: more tracks wins, HLS on a tie, unknown-language rows kept unless proven duplicates |
| AC-3 | unit | P0 | `SubtitleTrackMergerTest.merge_countsManifestByFromManifestFlag_notByRenditionUri` (new, D5) | a manifest track whose rendition did not match (`playerTrackUri == null`, `fromManifest = true`) counts as manifest in the per-language count |
| AC-3 | unit | P1 | `merge_matchesApiToRenditionByFilePath_whenLanguageOrderDiffers`, `merge_matchesSideLoadedTrackByStableUrl_whenHostAndTokenChange` (port), `merge_withoutApiFile_keepsRenditionUrlEmpty` (new, R4), `merge_doesNotPairSameLanguageVariants_withoutExactIdentity`, `merge_doesNotTreatDisplayLabelAsTrackIdentity` | enrichment (url, sourceFile, forced) only on unique identity match |
| AC-3 | unit | P1 | `merge_groupsVariantsByLanguage_keepingManifestOrder`, `merge_marksPartialVariant_andOrdersItLastWithinItsLanguage`, `merge_exposesOnlyOff_whenPlayerReportsNoTracks` | ordering, forced last, Off first |
| AC-4 | unit | P0 | `SubtitleTrackSelectorTest.select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero` (new: three candidates with group ids `1:0`,`2:0`,`3:0`, format ids `1:<k1>`.. ) | Q1 core: prefix-stripped identity distinguishes side-loads; the stripped group id alone cannot |
| AC-4 | unit | P0 | `select_survivesTrackGroupsBeingReordered` (port) and `select_prefersIdentityOverFullGroupId_whenChildOrderChanged` (new) | R3: after a reorder, identity wins over a stale `groupId` + index |
| AC-4 | unit | P0 | `select_usesFullGroupIdAndIndex_whenIdentityIsAbsent`, `select_doesNotFallBackToCoordinates_whenNothingIdentifiesTheTrack` (port of `select_returnsNull_ratherThanGuessing...`; delete the port of `select_fallsBackToCoordinates...`) | order is identity, group id + index, unambiguous language; never position |
| AC-4 | unit | P0 | `select_matchesByLanguage_onlyWhenUnambiguous`, `select_rejectsReusedCoordinatesAndIds_withDifferentLanguage`, `select_rejectsForcedTrack_forFullSubtitleRow` (port) | semantic filter first; ambiguity yields null |
| AC-4 | unit | P1 | `SubtitleTrackIdentityTest`: `withoutMergedSourcePrefix_stripsOneLeadingNumberColon`, `..._keepsIdsWithoutPrefix`, `..._doesNotStripEmbeddedColons`; port of the three `stableSubtitleKey_*` plus `sameSubtitleIdentity_treatsFileAndIndexM3u8AsOne` | the shared strip helper and `<file>/index.m3u8` normalisation |
| AC-5 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows` (new) | `onTracksUpdated(subtitleTracks = null)` leaves rows equal to the API rows, no merge |
| AC-5 | unit | P0 | `PlaybackMediaItemSpecTest.progressive_keepsAllApiSubtitles_includingEmbedTrue_withoutM3u8Mime` (new) | D2: no `embed` filter, no HLS mime on progressive; same specs as master `PlaybackController.kt:606-635` |
| AC-5 | device | P1 | checklist D-5 | progressive title: Off by default, same rows, side-load renders |
| AC-6 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_keepsSelectedRow_whenSameMergedRowStillExists` (new) | selection preserved by identity across a second report |
| AC-6 | unit | P0 | `..._selectedRowReordered_followsRowToNewIndex` and `..._selectedRowDisappears_goesOffAndDisablesText` (new) | re-find by identity; a dropped row ends at Off and `selectSubtitle(off)` is sent (picker never says Off while text plays) |
| AC-6 | unit | P0 | `..._identicalRepeatedReports_sendSingleReSelect` (new, R5) | exactly one `selectSubtitle` re-send, no loop; equal rows are not re-emitted (state emission count) |
| AC-6 | unit | P1 | `..._audioEmptyUpdate_keepsAudioRowsAndIndex`, `..._textOnlyUpdate_updatesSubtitleRows` (port of `tracksUpdated_keepsAudioTracks_whenUpdateCarriesOnlyTextTracks`) | D4 guard change: text-only report updates rows, audio kept |
| AC-6 | unit | P1 | `AudioTrackPreferenceResolverTest.findSameSubtitleTrackIndex_*`: off to off; by source identity; by full group id + index; by unambiguous canonical language; ambiguous gives Off | the new resolver method (no resolver tests exist today; new file under `vm/`) |
| AC-6 | unit | P1 | `PlayerVMTrackSelectionTest.refreshStreamLinks_recomputesApiRows_andMergesWithPlayerRows` (new; stub `mapper.mapSubtitleTracks`) | D11 `apiSubtitleTracks` recompute |
| AC-7 | unit | P0 | existing `tracksUpdated_restoresPreferredSubtitleByUrl_beforeLanguage`, `..._ByStableUrl_whenSignedUrlChanges`, `..._ambiguousSubtitleLanguage_withoutUrl_reappliesOffRow`, `..._reappliesOffRow_whenSavedSubtitleDoesNotResolve`, `..._restoreDoesNotRewritePreferencesFromIntermediateState` rerun over merged rows (feed `subtitleTracks` non-null in a parametrised variant) | master restore behaviour unchanged |
| AC-7 | unit | P0 | `PlayerVMTrackSelectionTest.tracksUpdated_restoresSavedUrl_onApiLinkedManifestRow` (new) | rows enriched with API `url` resolve a saved URL |
| AC-7 | unit | P1 | `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_matchesRuAgainstRus_viaSameSubtitleLanguage` (new, D12) and `..._stillAmbiguous_returnsNull` | canonical language comparison replaces `==` |
| AC-7 | unit | P2 | `SubtitleLinkTest` extension: payload without `file` parses to `file == null`; payload with `file` maps | D3 additive field |
| AC-8 | unit | P0 | `SubtitleLabelerTest` (new; absorbs every case of `SubtitleLabelsTest`: 3-letter and bibliographic codes, locale names, ordinals, forced qualifier, AI code, unknown "Subtitles N", Russian ISO-3 rule) with `FakeResourceProvider` | API-row labels byte-identical to master |
| AC-8 | unit | P1 | upstream `SubtitleLabelerTest` ports: `apply_neverShowsSubtitleFileNames`, `apply_neverShowsCdnChannelNumbering`, `apply_usesManifestLabels_whenTheyReadAsNamesAndSeparateTheRows`, `apply_keepsPartialMarker_whenAManifestLabelSeparatesTheRows`, `apply_doesNotQualifyRowsThatAlreadyReadDifferently`, `apply_preservesUnknownLanguageInformation_beforePositionalFallback` | manifest rows never show raw ids/file names; no raw ids |
| AC-8 | unit | P1 | `subtitleLabeler(resources)` factory test: strings come from `FakeResourceProvider` keys `player_subtitle_ai_generated/_forced/_variant_label/_unknown` | D10 wiring, locale from `resources.locale` |
| AC-8 | unit | P1 | `PlayerUIMapperTest` subtitle cases | mapper emits `isForced`, `sourceFile`, labels via labeler, Off row first |
| AC-8 | manual | P2 | wording `(owner judges)`, checklist D-2 label observation | final label text is the owner's call |
| AC-9 | UNTESTABLE | n/a | see below | Media3 trace D15 |
| AC-10 | device | P2 | checklist D-6 | TTFF and warm cache, observation |
| AC-11 | unit | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | gate; JUnit 5, `FakeResourceProvider` where strings are mapped; new classes listed above all exist |

Row counts: P0 = 20 (18 unit incl. the AC-11 gate, 2 device), P1 = 13 (12 unit, 1 device), P2 = 3 (1 unit, 1 manual, 1 device). P0 must exist before review.

### 3. Failing-first test (feature, not a bug fix)

Master drops API subtitles on HLS and the VM never sees player text tracks. Verified by reading base code:
`git -C {wt} show a97ee2b1:app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` lines 651-675
(`HlsMediaSource.Factory(...).createMediaSource`, subtitleConfigurations unread) and `PlayerVM.kt:211` (`onTracksUpdated`
takes audio only). Written first, in this order, each fails on base (does not compile or does not pass) and passes after:

1. `PlayerVMTrackSelectionTest.tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable` (AC-1/2 user-visible
   behaviour through VM state; on base the callback has no subtitle parameter, so rows can never change).
2. `SubtitleTrackSelectorTest.select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero`
   (the Q1 defect: the literal AC-4 rule cannot tell side-loads apart; base selector `findTextTrack` is positional-free
   but cannot see prefixed ids from a merged source, `PlaybackController.kt:866-942`).
3. `PlaybackMediaItemSpecTest.hls_attachesEveryApiSubtitle_...` (D1 input).

### 4. Test specifications and port notes

Builder helpers: copy `manifestTrack`, `apiTrack`, `playerTrack`, `offTrack` helpers from upstream tests and adjust to
`SubtitleTrackUIState` without `index`, with `fromManifest`. Do not mock the merger, selector, labeler or resolver in
VM tests: they are pure and stateless (D16). The controller is mocked (it is a collaborator, as in the existing fixture).

`PlaybackMediaItemSpecTest` (pure): `PlaybackMediaItemFactory` must expose the subtitle specs (id, label, language, mime,
selection flags, uri string) as a pure function so the test needs no `Uri`. Cases: HLS gets m3u8 mime; progressive does
not; id = label = `stableSubtitleKey(url)`; `forced = true` sets FORCED; `forced = null/false` does not; empty list;
`.srt`/`.vtt` mime by extension (reuse the old `subtitleMimeType` cases); `embed = true` row is kept on both transports.
If the implementer cannot expose such a function, this is a design defect to bounce, not a reason to drop the test.

Merger port notes: drop upstream cases that depend on `playerTrackUri != null` meaning manifest; add D5 case (manifest
row, `fromManifest = true`, no matched rendition). Add: unknown-language side-load identical (by identity) to a manifest
row is dropped, unknown-language non-duplicate kept. Add a determinism case: `merge(a, b) == merge(a, b)` (R5 guard).

Selector port notes: remove upstream `select_fallsBackToCoordinates_whenTheManifestExposesNoIdentity` and any
`matchByCoordinates` use (replace by its negation, listed above). Keep the AC-4 "never by position" assertion as an
explicit test: two same-language candidates, row without identity, same index as one of them, expected `null`.

VM re-send test (R5): after the first report call `verify(exactly = 1)`-style on the controller `selectSubtitle`, send the
same report again, assert still 1 and `state.subtitleTracks` emitted once (equal rows, no re-emit).

### 5. Q1 / Q2 / D4 sensitivity

- Q1 rejected (owner keeps literal "prefix-stripped group id"): the three selector P0 tests above that assert identity
  precedence change. Replace by a test that two side-loads (stripped group id "0" for both) are **not** distinguishable by
  group id alone and the selector must then return null or fall through to language, which would make AC-1/AC-2 with two
  same-language API subtitles non-selectable. That outcome contradicts AC-1 in practice; I would flag it back to the
  owner rather than weaken the tests. The identity-stripping tests in `SubtitleTrackIdentityTest` stay either way.
- Q2 rejected (owner wants a test, not a trace): needs Robolectric or the P6 instrumented fixtures; AC-9 becomes a
  P1 `instrumented` test `PlaybackControllerSubtitleFailureTest` (HLS fixture from `player-test-fixtures` plus a 404
  side-load, assert no `onPlayerError`, no CDN fallback, video position advances), run only through `make DEVICE=<serial> itest`.
  That moves work out of this run's M size and onto P6.
- D4 rejected (merge on progressive as upstream): AC-5 changes; delete `tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows`
  and the progressive factory case about unchanged rows, add the upstream container-track cases
  (`c798fdcb`, `5a43798a`) and a progressive-merged-rows device check. The `embed` filter question (D2) then reopens.

### 6. UNTESTABLE

- AC-9 (failing side-load 404/TLS does not break playback): needs a real `ExoPlayer` + failing source on the JVM
  (no Robolectric) and no hermetic title exists; evidence is the Media3 1.11.0 code trace in D15, recorded in the spec.
  Residual risk R2 (partial-then-failed subtitle stalling the merged period) is watched during the device pass only.
- Text-off default at build (`PlaybackController.kt:393`) and the parameter building in `selectSubtitle` /
  `applySubtitleTrackSelection`: need a real `ExoPlayer`; covered by device D-1..D-5 only, left to
  playback-controller-split / P6 for automation.
- Compose focus behaviour in `SubtitlesPage` (R1): no Compose UI test infrastructure for TV focus in this project;
  device only.
- `PlaybackController` warm-up shape (D13): unchanged code; observed only (D-6).

### 7. Manual / device checklist for qa-device

Preconditions: authenticated emulator, lease via `tools/emulator-resource-lock.sh`, worktree dev APK installed with
`tools/android-apk-install-preserve`, `adb -s <serial>` on every call. Owner supplies (Q6) before verify: title H1 =
HLS whose API subtitle list contains a language the manifest lacks AND a manifest language the API lacks; title H2 = HLS
where both offer one language (R4); optional H3 with a forced track; title P1 = progressive-only. Evidence files must
not include tokens or raw logcat (run `tools/mobile-evidence-audit.sh`).

D-1 (AC-1). Open H1 (HLS). Wait for playback; D-pad centre to open the overlay, move to Subtitles, open the page.
Expected: the API-only language is listed with a language-name label; select it; cues render within a few seconds.
Must not: raw id, file name or `1:xxx` text as a label; Off shown while cues render; duplicate rows for one language.
D-2 (AC-2, AC-8). On the same page select the manifest-only language. Expected: listed with a language name (not a
rendition id), selecting it renders cues and the row stays highlighted. Must not: subtitles turn off after the pick (D9);
picker jumps back to Off. Record the label text for the owner.
D-3 (AC-3). On H2: the shared language appears exactly once. Expected: one row per language. Must not: two rows for one
language unless they are distinct variants (forced, AI) with qualifiers.
D-4 (AC-4/6/R3). On H1 select an API-only language, then switch quality (OK, Quality, other quality) and then back.
Expected: the same language remains selected and rendering after each switch. Must not: a different-language subtitle
renders (positional mis-pick), picker says Off while cues show, or cues stop with the picker still showing a language.
D-5 (AC-5). Open P1 (progressive). Expected: subtitles Off by default; rows identical to the API list as on master; pick one,
it renders. Must not: extra container or manifest rows, changed order, changed labels.
D-6 (AC-10). On H1 cold start twice, with and without a warm episode (series next-episode prefetch if available). Expected:
time to first frame comparable to master build of the same title (note both numbers; subjective regression over about
half a second is a finding). Must not: visible extra buffering or a subtitle HTTP fetch before a subtitle is picked
(optional: `adb -s <serial> shell dumpsys netstats` is not needed; observing no regression is enough).
D-7 (R1). Open the Subtitles page immediately at start (during first report) and during a quality switch. Expected:
focus stays inside the panel, D-pad Up/Down still moves. Must not: focus leaves the panel, crash, empty page.
D-8 (AC-7). On H1 pick a subtitle, back out, reopen the same item. Expected: the saved subtitle is restored (same as master
behaviour). Must not: Off, or the wrong language.
D-9 (AC-9, optional). If a title with a dead subtitle URL turns up: play it. Expected: video plays, no error dialog, no
CDN fallback. Must not: fatal error, repeated rebuffering.

### Counts

See the row counts under the traceability table. Manual device steps: 9 (D-1..D-9; D-9 optional).

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | AC-4 wording: stripped group ids are all "0" for side-loads | yes | answered | Reword: source identity, then full TrackGroup.id + index, then unambiguous language; never position |
| Q2 | AC-9 evidence without Robolectric | no | answered | Media3 trace (D15); hermetic test in P6; device check if a failing title turns up |
| Q3 | Keep side-loaded subtitle hosts out of the debug stream-source line | no | answered | Not here; moved to playback-controller-split |
| Q4 | Embedded container subtitles on progressive | no | assumed | Left out (keeps AC-5) |
| Q5 | Device-check titles (H1, H2, optional H3, P1) | no | assumed | qa-device picks titles (owner: proceed on recommendations) |
| Q6 | Manual quality plays url.hls (no subtitle renditions, no audio names) | no | assumed | Separate run manual-quality-hls4 (recommended) |
| Q7 | Re-label Outlander as the manifest-only title (AC-2), keep AC-1 open, qa records a count of S1E5 API subtitles without storing the response | no | assumed | Yes (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-12 | Option C, two steps; this run is step 1 (size M) | owner |
| 2026-10-12 | Labels: upstream SubtitleLabeler with the first batch's strings and Russian ISO-3 rule | owner |
| 2026-10-12 | Merge rule: upstream 9bd835b0 | owner |
| 2026-10-14 | AC-4 reworded (Q1) | owner |
| 2026-10-14 | AC-9 evidence: Media3 trace D15 + P6 hermetic test (Q2) | owner |
| 2026-10-14 | Debug stream-source filter for side-loads goes to playback-controller-split (Q3) | owner |
| 2026-10-14 | Gate 1: spec approved | owner |
| 2026-10-14 | Design delta 2 (D17-D20, D11 amended): text-less report is final, VM keeps the intended pick; device check D-4 reworded accordingly | architect, conductor |
| 2026-10-14 | Q6/Q7 taken as recommended under the owner's standing instruction | conductor |
