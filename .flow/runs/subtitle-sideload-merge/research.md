# Research: subtitle-sideload-merge

## 1. Request

"Port the rest of upstream's subtitle work: side-load API subtitles on HLS (003d4c55) and the full subtitle merge model; starts after subtitle-upstream-port lands"

## 2. Understanding

On HLS streams the fork's subtitle picker lists only what the KinoPub API returns, and the player can only play what the HLS manifest declares. The API subtitle files are attached to the `MediaItem`, but the HLS source the fork builds discards them. Upstream (rovkinmax/Puber) fixed both halves. It side-loads every API subtitle next to the HLS stream. It then builds the picker from the tracks the player actually discovered (manifest renditions plus side-loaded files), merged with API metadata: duplicates dropped, one source chosen per language, labels by language name, selection by a merge-stable group id, and the choice restored across player rebuilds. This run brings that model into the fork on top of the first batch (`subtitle-upstream-port`), which only reworked the API-only list (Off by default, no positional match, language/AI/forced labels).

## 3. Current state in code (master `be8395a4`)

- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:599-614`: `buildMediaItem` attaches every API `SubtitleLink` as a `MediaItem.SubtitleConfiguration` (id and label = `stableSubtitleKey()`), for HLS too.
- `PlaybackController.kt:651-675`: `setMediaSource` builds HLS with `HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)` and `player.setMediaSource(...)`. That factory never reads `subtitleConfigurations` (see External findings), so on HLS the API subtitles are silently dropped. Only progressive goes through `setMediaItem`, where the player's `DefaultMediaSourceFactory` (`:464-476`) side-loads them. This HLS path has been there since `88cdf071` (Release 1.0.0).
- `PlaybackController.kt:748-764`: the warm-up player builds HLS the same way. It side-loads nothing, but it would have to match the main path's source shape if the cache key or source differs (`unverified` whether that matters).
- `PlaybackController.kt:64-84`: `PlaybackControl.Callback.onTracksUpdated(audioTracks, selectedIndex)` reports audio only. The ViewModel never sees the player's text tracks.
- `PlaybackController.kt:866-942`: `applySubtitleTrackSelection` / `findTextTrack` match an API row to a `Tracks.Group` by URL, then by stable key in `format.id`/`format.label`, then by position (`findTextTrackBySubtitleIndex`, removed by the first batch), then by an unambiguous language. If nothing matches, only `setPreferredTextLanguage` is applied.
- `PlaybackController.kt:542-556`: `selectSubtitle(null)` disables text. `pendingSubtitleTrack` is re-applied in `onTracksChanged` (`:234-237`).
- `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:35-75`: `mapSubtitleTracks` builds the picker from the API list only (Off + one row per `SubtitleLink`, with "variant N" labels for duplicate languages).
- `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIModels.kt:27-32`: `SubtitleTrackUIState(index, label, language, url)`. It has no player identity fields (upstream adds `isForced`, `sourceFile`, `descriptiveLabel`, `playerTrackId`, `playerTrackGroupId`, `playerTrackUri`, group/track index).
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:516-542`: `restoreTrackPreferences` uses `AudioTrackPreferenceResolver.findSubtitleTrackIndex` (`AudioTrackPreferenceResolver.kt:24-37`: exact URL, then stable key, then unambiguous language) over the API list. `PlayerVM.kt:792-808` applies and persists the choice. `PlayerVM.kt:1768-1774` saves lang and URL.
- `app/src/main/java/com/kino/puber/data/repository/PlayerPreferencesRepository.kt:16-50`: per-item subtitle lang and URL only (upstream 7d5addd2 adds forced and descriptive-label keys).
- `app/src/main/java/com/kino/puber/data/api/models/Models.kt:429-434`: `SubtitleLink(lang, url, shift, embed)`. The first batch adds `forced`. Upstream also has `file` and `shouldSideLoad(isHls) = isHls || embed != true`.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackIdentity.kt:1-12`: `stableSubtitleKey()`, the only subtitle helper in master. `SubtitleTrackMerger`, `SubtitleTrackSelector`, `SubtitleLabeler`, `SubtitleLanguage` and `PlaybackMediaItemFactory` do not exist.
- `app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt:19-26,232-245`: the fork carries `StreamType` (HLS / PROGRESSIVE) on each `StreamCandidate` (since `2353a735`). Upstream's `f38fbcf0` and `dc011a81` (HLS detection, stream container) are therefore already solved differently here.
- UI: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerSettingsPanel.kt:231-252` renders `subtitleTracks[i].label` and sends `SelectSubtitle(index)`. Upstream's panel change (04cff1ce) is label-only, so the fork's merged settings panel needs no structural change.
- Tests: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`, `PlayerVMTest.kt`, `PlayerVMTestFixture.kt`. There are no subtitle merger, selector or labeler tests and no instrumented player tests (those arrive with upstream-sync-triage P6).

### What the first batch already does (worktree `subtitle-player-fixes`, uncommitted, read-only inspection)

- `PlaybackController`: `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` at build, so subtitles start Off (the Off part of 6123fdbc). It sets `C.SELECTION_FLAG_FORCED` from `SubtitleLink.forced`. It removes the positional fallback.
- `Models.kt`: `SubtitleLink.forced`.
- New `ui/feature/player/model/SubtitleLabels.kt`, a fork-own equivalent of upstream `SubtitleLabeler`/`SubtitleLanguage` for the API list: ISO-3 language names, AI label, forced qualifier, ordinals. `PlayerUIMapper` uses it. Strings `player_subtitle_ai_generated`, `player_subtitle_forced`, `player_subtitle_unknown`.
- `ControlsStateMachine.initialize` and `PlayerVM.publishPreparedContent` (part of f3eb3533).
- It does NOT side-load on HLS, report player text tracks, merge, select by group id, or restore beyond lang and URL.

## 4. Related work

- **`subtitle-upstream-port`** (stage review, branch `subtitle-player-fixes`, uncommitted): the first batch above. Its `## Next` lists "side-load API subtitles on HLS (003d4c55), the full merge model" as later candidates. That is this run's source, so it is a successor, not a duplicate. Hard prerequisite: this run edits the same lines (`PlaybackController` text-track code, `PlayerUIMapper`, `Models.kt`, `SubtitleLabels.kt`).
- **`upstream-sync-triage`** (stage impl): spec row T5 (worktree `docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md:88`) lists the 28 subtitle commits and assigns them to `subtitle-upstream-port`, "listed only; not re-planned here", and says the not-yet-taken ones (003d4c55, merge model) stay there (`:157-158`, `:271-272`). Its P6 `player-hermetic-fixtures` waits for `subtitle-upstream-port` on master. It brings `:player-test-fixtures` and the instrumented `PlaybackController*Test`, the only practical home for upstream's `PlaybackControllerNetworkTracksTest` and `SubtitleVariantFixture` (instrumented, fixture-backed). It does not port any subtitle production code (AC-3, AC-17).
- **`playback-controller-split`** (idea, M): SOLID-4 wants to extract text-track matching (`PlaybackController.kt:884`) into a pure `TextTrackMatcher` (`docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:90`). Upstream's `SubtitleTrackSelector` (2d5d1c56) is exactly that extraction, so the two overlap. Whichever lands second has to rebase over the other. Recommendation: do this run first and narrow `playback-controller-split` to debug info and the package move (STR-3).
- **`player-vm-split`** (idea, L, after player-overlays-port): splits `PlayerVM`. This run adds merge and restore code to `PlayerVM`, so a sequencing conflict only.
- **`player-overlays-port`** (impl): touches `player/component/*` only. Low overlap, since this run's UI change is labels.
- Upstream commits (all by content; unrelated histories). "The full merge model" is the `SubtitleTrackMerger` lineage plus what it needs:
  - Merge core: c798fdcb (embedded discovery and merging, adds `SubtitleTrackMerger`, player tracks reported to VM), d717acc3 (same-language variants), d6709295 (HLS identity mapping, `SubtitleTrackUIState` player fields), cc446309 (disambiguate labels), 879fc86f (drop unreachable paths), 2d5d1c56 (`SubtitleTrackSelector`, select by merge-stable `TrackGroup.id`), 04cff1ce (`SubtitleLabeler`/`SubtitleLanguage`), 3c419d4e / 8b9e8df0 / d4d7163d (dedupe side-loaded vs manifest), e28f3e40, 435e5c53, 1c075436, 7a98b0d9 (cleanup), 5a43798a (embedded fallback and restore), 2cfb0f64 + 3618213e (AI/unknown labels; diagnostics added then removed), 9bd835b0 (per language keep the source with more tracks, HLS on ties: the final rule).
  - Side-load: 003d4c55 (hand-built `MergingMediaSource` + `SingleSampleMediaSource` around HLS), then replaced in c42dd8c1 by building HLS through `DefaultMediaSourceFactory` and moving item building to `PlaybackMediaItemFactory`. Upstream HEAD `b755826c` has no hand-built merge.
  - Restore and selection: a563d6f6 (harden preference restore), 6123fdbc (rest of it: merger and resolver restore, `PlayerVMSubtitleRestoreTest`), 7d5addd2 (restore across playback recreation; new `SubtitlePreference` with forced and descriptive-label pref keys), f9c9ff38 (forced and language-less selection, 2026-10-03).
  - Not applicable or covered: f38fbcf0 and dc011a81 (fork has `StreamType`), 85737187 (a string covered by the first batch's labels), 54fb3744 (test the fork lacks), most of c42dd8c1 (fixture/test adaptation).
- Upstream HEAD sizes for orientation: `SubtitleTrackMerger.kt` 154, `SubtitleTrackSelector.kt` 104, `SubtitleLabeler.kt` 106, `SubtitleLanguage.kt` 51, `SubtitleTrackIdentity.kt` 48, `PlaybackMediaItemFactory.kt` 49 lines. Unit tests are JUnit 5 (`SubtitleTrackMergerTest` 525, `SubtitleTrackSelectorTest` 156, `SubtitleLabelerTest` 130, `PlayerVMSubtitleRestoreTest` 295, `PlayerVMSubtitleVariantTest` 325, `PlaybackControllerSubtitleDefaultsTest`), which match the fork's convention.

## 5. External findings

Media3 version: `media3 = "1.11.0"` in `gradle/libs.versions.toml:27` (same as upstream).

- `HlsMediaSource` / `HlsMediaSource.Factory` (1.11.0 source) never read `localConfiguration.subtitleConfigurations`; the factory reads only `streamKeys`. `allowChunklessPreparation` defaults to `true`, so the fork's explicit `setAllowChunklessPreparation(true)` is redundant, and going through `DefaultMediaSourceFactory` loses nothing there. https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaSource.java
- `DefaultMediaSourceFactory.createMediaSource` (1.11.0) delegates HLS reflectively to `HlsMediaSource.Factory`. When subtitle configurations exist, it wraps the main source in a `MergingMediaSource`, one source per subtitle. By default (parse during extraction) these are `ProgressiveMediaSource` with `SubtitleExtractor`, lazy loading with a single track, and `setLoadOnlySelectedTracks`. Its `setLoadErrorHandlingPolicy` applies to delegates and to the subtitle sources. https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/DefaultMediaSourceFactory.java
- `MergingMediaSource` (1.11.0) Javadoc: TrackGroup and Format ids of children are rewritten to start with `i:` (0-based child index). Consequence: after side-loading, a side-loaded track's `format.id` becomes `"<n>:<stableKey>"`. The fork's `format.id == stableKey` match then fails and only the `format.label` match saves it. Upstream strips the prefix (`withoutMergedSourcePrefix`) and selects by `TrackGroup.id`. https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MergingMediaSource.java
- Reference page for the factory API: https://developer.android.com/reference/androidx/media3/exoplayer/hls/HlsMediaSource.Factory
- `unverified`: whether a side-loaded subtitle that fails to load (404, TLS) surfaces as a fatal player error under the fork's `PlaybackErrorPolicy` instead of being dropped. I found no primary source. It needs a test or a device check.

## 6. Options

**A. Side-load only (S).** Build HLS through the existing `DefaultMediaSourceFactory` (upstream c42dd8c1 shape; `PlaybackController.kt:651-675` and the warm-up at `:748-764`), keep the API-only picker, and fix matching to tolerate the `n:` id prefix.
- Pros: small, fixes "API subtitle missing from manifest is unreachable".
- Cons: every language the manifest also has now exists twice in the player. The picker still shows API rows only, so manifest-only subtitles stay invisible. Restore is still keyed on API URL and language. This is upstream's intermediate state, which it abandoned within a day (3c419d4e..d4d7163d, 9bd835b0).

**B. Port upstream's final subtitle model as one change (L).** Side-load via `DefaultMediaSourceFactory` plus `PlaybackMediaItemFactory`. Report text tracks through `PlaybackControl.Callback.onTracksUpdated`. Extend `SubtitleTrackUIState`. Add `SubtitleTrackMerger` (9bd835b0 rule), `SubtitleTrackSelector` (group id), and `SubtitleLabeler`/`SubtitleLanguage`, reconciled with the first batch's `SubtitleLabels.kt`. Add the `PlayerVM` merge and restore and `PlayerPreferencesRepository` `SubtitlePreference` (7d5addd2, f9c9ff38). Port from the upstream HEAD state, not commit by commit.
- Pros: one coherent model. Upstream's JUnit 5 tests come almost as-is.
- Cons: crosses the controller contract, VM, UI model and a persisted data shape. Two shippable goals in one diff, and the biggest conflict surface with `playback-controller-split` and `player-vm-split`.

**C. Port the final model in two steps (M + M), recommended.**
- Step 1: side-load, player text tracks to the VM, merger, selector, labeler, keeping the existing lang and URL restore through the resolver.
- Step 2: restore across playback recreation, forced and language-less selection, and the new preference keys (7d5addd2, f9c9ff38, the restore part of 6123fdbc and a563d6f6), as a follow-up run.
- Pros: each step is a shippable, device-checkable goal. Step 1 already fixes the user-visible issue (missing or duplicated subtitles on HLS). It also takes over `playback-controller-split`'s text-track extraction.
- Cons: step 1 must keep restore working with merged rows. Upstream's resolver is keyed on the same lang and URL, so this is likely fine (`unverified` until tested).

**Recommended: C.**

## 7. Risks

- **Prerequisite churn.** `subtitle-upstream-port` is uncommitted in its worktree, and this run rewrites the same code (positional match, labels, `PlayerUIMapper`). Start only after it is on master, as the request says. Also decide whether `SubtitleLabels.kt` gives way to upstream's `SubtitleLabeler` or the other way round.
- **Merged id prefix.** `MergingMediaSource` rewrites ids, so any id-based match or persisted id has to strip `n:`. A source rebuild (`switchStream`, stream fallback) can change child order.
- **Side-loaded subtitle load failure** may become a playback error (`unverified`, see External findings). This hits CDN-fallback and network-diagnostics behaviour.
- **Network and cache.** Extra subtitle fetches per start go through the shared `CacheDataSource`. With `loadOnlySelectedTracks`, unselected side-loads should not download (`unverified` on device).
- **Warm-up player.** Changing the HLS source shape without changing the warm-up could make the warm cache miss (`unverified`).
- **Data shape.** Step 2 adds preference keys. They need old-to-new compatibility, because existing per-item lang and URL prefs must still restore.
- **Device-only evidence.** The real behaviour needs a KinoPub HLS title whose API list and manifest differ, which exists only with an authenticated backend on the emulator. The first batch's device pass did not see DEFAULT/FORCED manifest subtitles or forced/variant labels. The hermetic `PlaybackControllerNetworkTracksTest` depends on P6 `:player-test-fixtures`.
- **Conflicts.** `playback-controller-split`, `player-vm-split` and upstream-sync-triage P6 all touch `PlaybackController`/`PlayerVM`.

## 8. Open questions

1. **Port the final upstream state or replay the 28 commits?** Recommended: the final state at `b755826c`. About half the commits add and then remove code (diagnostics, hand-built merge, forced-URL guess).
2. **Scope: one run (B) or two steps (C)?** Recommended: C. This run covers side-load and the merge model. Restore across recreation with the new pref keys is a follow-up run.
3. **Which labeler survives: the first batch's `SubtitleLabels.kt` or upstream's `SubtitleLabeler`/`SubtitleLanguage`?** Recommended: upstream's, fed the first batch's strings and its rule for the Russian ISO-3 name, so that labels apply to merged player rows and not only API rows.
4. **Rule when the manifest and the API both offer a language?** Recommended: upstream 9bd835b0. Per language keep the source with more tracks, HLS on a tie, and keep unknown-language rows unless proven duplicates.
5. **Order against `playback-controller-split`?** Recommended: this run first. That run then drops its text-track half (covered by `SubtitleTrackSelector`) and keeps debug info and the package move.
6. **Test title for the device check.** Recommended: the owner names one HLS title (id only, no account data) whose API subtitle list differs from its manifest, plus one with a forced track. Instrumented fixture tests wait for P6.

## 9. Sources

- Files (master): `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`, `PlayerVM.kt`, `AudioTrackPreferenceResolver.kt`, `SubtitleTrackIdentity.kt`, `PlaybackErrorPolicy.kt`; `ui/feature/player/model/PlayerUIMapper.kt`, `PlayerUIModels.kt`; `ui/feature/player/component/PlayerSettingsPanel.kt`; `data/api/models/Models.kt`; `data/repository/PlayerPreferencesRepository.kt`; `domain/interactor/player/PlayerInteractor.kt`; `gradle/libs.versions.toml`; `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`.
- Worktree (read-only): `/home/user1/orca/workspaces/Puber/subtitle-player-fixes` (`git status`, `git diff HEAD -- app/src/main`, `SubtitleLabels.kt`); `/home/user1/orca/workspaces/Puber/upstream-sync-triage/docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`.
- Runs: `subtitle-upstream-port`, `upstream-sync-triage`, `playback-controller-split`, `player-vm-split`, `player-overlays-port`, `perf-deferred`, `player-position-state`.
- Fork commits: `88cdf071`, `2353a735`, `a6043290`, `be8395a4`.
- Upstream commits: c798fdcb d717acc3 d6709295 85737187 f38fbcf0 cc446309 a563d6f6 879fc86f 003d4c55 2d5d1c56 04cff1ce 3c419d4e 8b9e8df0 d4d7163d e28f3e40 435e5c53 1c075436 dc011a81 7a98b0d9 5a43798a 54fb3744 c42dd8c1 2cfb0f64 3618213e 9bd835b0 6123fdbc 7d5addd2 f9c9ff38 f3eb3533, HEAD b755826c.
- URLs:
  - https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaSource.java
  - https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/DefaultMediaSourceFactory.java
  - https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MergingMediaSource.java
  - https://developer.android.com/reference/androidx/media3/exoplayer/hls/HlsMediaSource.Factory
