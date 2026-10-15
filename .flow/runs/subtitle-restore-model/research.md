# Research: subtitle-restore-model

## 1. Request

"Restore the chosen subtitle across player recreation with upstream's SubtitlePreference keys (7d5addd2, f9c9ff38, a563d6f6)"

Log context (state.md): step 2 of option C from `subtitle-sideload-merge`. Also the deferred finding from `subtitle-upstream-port` triage-2 #1: `prepare()` clears `pendingSubtitleTrack`, so on a stream with no audio group a same-media rebuild (buffer preset, Fast DNS) drops the live pick.

## 2. Understanding

Master `f351d988` already restores the subtitle within one player session. The VM keeps the viewer's pick (`intendedSubtitleRow`), re-finds it after every track report, and re-arms the restore when the player is rebuilt. What it saves to disk is only the row's language and API URL. A row that came only from the HLS manifest has no API URL. If such a row has no language, or shares its language with a forced or differently labelled variant, the saved data cannot point back to it. When the player is opened again, the subtitle then comes back as Off. Saving a language-less row even writes Off over the previous preference. The same thin data lets a forced-only track replace the full one. Upstream fixed this with a `SubtitlePreference` (language, URL or track key, forced flag, readable label) and a resolver that first filters rows by those semantics. This run ports that, fitted to the fork's intent model, and closes the deferred audio-less rebuild item, which the code now mostly covers already.

## 3. Current state in code (master `f351d988`)

App paths are relative to `app/src/main/java/com/kino/puber/`.

**Deferred item (audio-less rebuild): closed in code, no test pins it**
- `ui/feature/player/vm/PlaybackController.kt:818-846` `notifyTracksUpdated` now returns early only when there are **neither** audio nor text groups (`:822`). It changed in `c6ea3789`; before that it was `if (audioGroups.isEmpty()) return`, see `git show 50f46021:...PlaybackController.kt:855`. So a stream that has text but no audio still reports to the VM.
- `PlaybackController.kt:385-387` `prepare()` still clears `pendingSubtitleTrack`. Under D19 that field is only a mirror of the displayed row (`.flow/runs/subtitle-sideload-merge/design-architect-2.md` D19), and the VM re-sends it.
- `ui/feature/player/vm/PlayerVM.kt:927-951` buffer preset and Fast DNS reset `tracksRestoredForCurrentMedia` and `subtitleRestoredForCurrentMedia` but keep `intendedSubtitleRow`. `871b06a6` keeps the intent.
- `PlayerVM.kt:535-578` `onTracksUpdated` works as follows:
  - It re-finds the intent in the new rows (`:545-550`). An audio-empty report keeps the audio rows (`:552-557`).
  - The subtitle restore waits for a report that has text (`:567-572`).
  - When the displayed row changes, it re-sends it (`:574-577`).
- `PlayerVM.kt:599-616` `restoreSubtitlePreference` applies the **saved** preference. If nothing resolves, it re-sends the displayed row (the intent) without touching it.
- What is left of the item:
  - (a) No test combines a rebuild with an audio-less report. The rebuild tests `PlayerVMTrackSelectionTest.kt:401-457` use the default `audioReport`, and `tracksUpdated_textOnlyUpdate_updatesSubtitleRows` (`:695`) covers no rebuild.
  - (b) A stream with neither audio nor text groups never reports, but it has nothing to restore. The sideload-merge spec already concluded this: `docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md:92-94` says "re-test this, close the audio-less part".

**Persisted data shape (what this run changes)**
- `data/repository/PlayerPreferencesRepository.kt:16-22,28-56,119-122` stores two subtitle values per item: `subtitle_lang_<id>` and `subtitle_url_<id>`. A null value removes the key.
- `domain/interactor/player/PlayerInteractor.kt:335-358` has `getPreferredSubtitleLang`/`Url` and `saveTrackPreferences(itemId, audioLang, audioLabel, subtitleLang, subtitleUrl)`.
- `PlayerVM.kt:1849-1871` `saveTrackPreferences` saves `intendedSubtitleRow.language` and `.url`, each only when non-empty. While the restore is pending it passes the stored values through instead.
  - **Gap G1:** a manifest-only row has `url = ""`. Rows are built at `ui/feature/player/vm/PlayerTextTracks.kt:42-54`, and only a unique identity match with an API row fills the URL (`SubtitleTrackMerger.kt:147-153`). If such a row also has no language, picking it saves `null`/`null`. That removes both keys, so the pick is persisted as Off.
  - **Gap G2:** for a manifest-only row with a language, only the language is saved.

**Restore resolver (cross-session path)**
- `ui/feature/player/vm/AudioTrackPreferenceResolver.kt:26-39` `findSubtitleTrackIndex(tracks, lang, url)` tries three rules in order: exact URL, stable key, unambiguous language (`:151-158`, which ignores `isForced` and labels).
  - **Gap G2 (continued):** two same-language rows, for example full and forced, or two labelled variants, are ambiguous, so the restore gives Off.
  - **Gap G3:** if only a forced row of that language exists, the language rule picks it. It then becomes the intent with `persist=false` (`PlayerVM.kt:606-608`, `869-881`), so after a rebuild or re-entry a full-subtitle viewer silently gets the forced track. This is confirmed by reading the code; no test covers it.
- `AudioTrackPreferenceResolver.kt:48-111` `findSameSubtitleTrackIndex`, the in-session re-find, already has these rules: player track id, source identity, full group id plus index with compatible language and forced, then unambiguous language with compatible forced. It does not use the descriptive label.
- `PlayerVM.kt:599-616` order on a rebuild: it resolves the **saved** preference first, not the live intent. Upstream uses `previousSubtitle ?: saved` (7d5addd2, `PlayerVM.restoreSubtitlePreference`).

**Already on master from the four upstream commits (do not re-port)**
- a563d6f6:
  - Separate audio and subtitle restore; the subtitle restore waits for text (`PlayerVM.kt:163-165,563-572,584-616`).
  - An audio-empty report keeps the audio rows (`:552-557`).
  - Save passes through while the restore is pending (`:1855-1862`).
  - The bounded retry counter is replaced by the "wait for text" rule.
- 6123fdbc:
  - Text disabled at build (`PlaybackController.kt:400-404`).
  - Language-less merge rule (`SubtitleTrackMerger.kt:48-60`).
  - Group id in the identity match (`AudioTrackPreferenceResolver.kt:86-90`).
  - Intent kept on rebuild, through D19 instead of `subtitleToRestore`.
- 7d5addd2:
  - Selector semantic filter (language, forced, readable label) at `SubtitleTrackSelector.kt:44-64`.
  - `isForced` on `PlayerTextTrack` (`PlayerTextTracks.kt:85`).
- f9c9ff38:
  - FORCED selection flag on side-loads (`PlaybackMediaItemFactory.kt:50,68`).
  - Language-less identity in the selector (`SubtitleTrackSelector.kt:56-57` skips the language check for a blank row language).

**Not on master (what this run would add)**
- 7d5addd2:
  - `SubtitlePreference(language, url, isForced, descriptiveLabel)` and the keys `subtitle_forced_<id>` and `subtitle_label_<id>` (forced is read through `contains()`, so it can be null).
  - Interactor `getPreferredSubtitlePreference`.
  - Resolver additions: semantic filter, Off on an empty language only when no identity is given (from f9c9ff38), identity over `url/sourceFile/playerTrackUri/playerTrackId/playerTrackGroupId`, descriptive-label match, and a language match that prefers manifest rows.
  - `preferenceKey()`, which stores in the URL key, in order: url, then sourceFile, then the rendition group id, then the rendition URI, then the track id.
  - `disableSubtitlesForSourceReplacement` in `switchStream`.
- f9c9ff38: in `subtitleLanguageMatch`, an empty language means "no match" rather than Off.
- Fork `switchStream` (`PlaybackController.kt:501-521`) keeps the old `trackSelectionParameters`, which leaves text on with the preferred language set, until the pending row is re-applied in `onTracksChanged` (`:244-247`). Whether this shows a wrong track for a moment is `unverified`.

**Tests**
- `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt` (restore, rebuild and intent cases), `AudioTrackPreferenceResolverTest.kt`, `SubtitleTrackSelectorTest.kt`, `SubtitleTrackMergerTest.kt`, `PlayerTextTracksTest.kt`.
- `app/src/test/kotlin/com/kino/puber/data/repository/PlayerPreferencesRepositoryTest.kt` exists. Upstream extends its counterpart with `subtitlePreferenceMetadata_persistsAndClearsSemanticIdentity`.
- Upstream unit tests to adapt:
  - `PlayerVMSubtitleRestoreTest` at `b755826c`, 11 cases, for example `qualityChange_keepsOff_whenOnlyForcedVariantCanReplaceSelectedFullVariant` and `recreation_restoresSavedRendition_afterQualityAndAudioChanges`.
  - Resolver cases `rejectsForcedOnlyReplacementForFullPreference`, `usesMeaningfulLabelToResolveSameLanguageVariants`, `preservesLanguageLessTrackByIdentity` and `doesNotRestoreOffWhenLanguageLessIdentityDisappears`.
  - VM variant case `tracksUpdated_preservesSelectedLanguageLessTrackOnRepeatedAndReorderedCallbacks`.
  - The sideload-merge spec (`:480-481`) explicitly left these for this run.

## 4. Related work

- **subtitle-sideload-merge** (done, merged as `6fcaddf0`) is the parent; this run is step 2 of its option C, and its spec "Out of scope" (`docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md:43`) names this run's scope word for word. Its D17-D20 already redesigned the in-session restore. This run extends that model and does not replace it. Relevant commits: `c6ea3789` (audio-or-text guard), `f4b311e0` (D17-D19), `871b06a6` (rebuild keeps intent).
- **subtitle-upstream-port** (done, `50f46021`): triage-2 #1 is the deferred audio-less item. As shown above, the code closes it and only a test is missing.
- **playback-controller-split** (idea, M): it already lost text-track matching to `SubtitleTrackSelector`. It keeps "text-off-at-build test, debug-info extraction, package move". It overlaps this run only if Option C below changes `switchStream`.
- **player-vm-split** (idea, L, after player-overlays-port): it extracts coordinators from `PlayerVM`, and this run edits the `PlayerVM` restore and save. That is a sequencing conflict only. Neither run covers the other.
- **upstream-sync-triage P6** (impl, waiting): it brings `:player-test-fixtures` and ports only those `PlaybackControllerNetworkTracksTest` cases that need no production change (spec D24, worktree `docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md:321`). The instrumented cases of 7d5addd2 and f9c9ff38 (for example `hlsTracks_selectsForcedApiFallbackAndRendersItsCue`) would need P6's fixtures. Treat them as a follow-up after P6, not part of this run.
- **manual-quality-hls4** (idea): it changes which stream manual quality plays. That is unrelated to persistence, but it changes the scenario "quality switch to a stream without the row".
- **Duplicate: none.** No run covers SubtitlePreference persistence or the cross-session resolver.

## 5. External findings

Media3 version: `1.11.0` (`gradle/libs.versions.toml`, as recorded by the previous run).

- `HlsMediaPeriod` (1.11.0) builds each subtitle rendition's `TrackGroup` id as `"subtitle:" + name`, from the rendition NAME and shared across redundant groups and variants. So a manifest row's group id is stable across quality variants and player recreation, as long as the manifest NAME stays the same. https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaPeriod.java
- `MergingMediaPeriod.onPrepared` (1.11.0) rewrites `TrackGroup.id` and `Format.id` to `i + ":" + id`, where `i` is the child index (`selectTracks` parses the index back from the prefix). In the fork the main HLS source is child 0 (spec D5). The consequences:
  - A manifest row's group id is `"0:subtitle:<NAME>"` when the item has side-loads.
  - It is `"subtitle:<NAME>"` when the item has none.
  - Persisting the full group id would therefore fail to match when the API subtitle list changes between sessions. Strip the merge prefix (the existing `withoutMergedSourcePrefix`, `SubtitleTrackIdentity.kt`).
  - Side-loaded children (`"<n>:0"`) are positional and must never be persisted.
  - https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MergingMediaPeriod.java
- Upstream `rovkinmax/Puber` HEAD is still `b755826c` (2026-10-05), so no restore code has changed upstream since f9c9ff38 (`git log f9c9ff38..b755826c` on the resolver, VM, selector and repository shows only workflow commits).

## 6. Options

**A. Close the deferred item only (S).** Add one VM test: a rebuild (Fast DNS and buffer preset), then an audio-less report with text, then assert the pick is re-sent and nothing is persisted. Record the item as closed by `c6ea3789` and `871b06a6`.
- Pros: tiny and ready now.
- Cons: leaves G1-G3. A manifest-only pick is lost on re-entry, a language-less pick is persisted as Off, and a forced track can replace a full one. That is the actual request.

**B. Port upstream's SubtitlePreference and semantic restore onto the fork's intent model (M), recommended.**
- Repository and interactor:
  - Add `SubtitlePreference(language, url, isForced, descriptiveLabel)`.
  - New keys `subtitle_forced_<id>` (Boolean, read with `contains()` so a missing key is null) and `subtitle_label_<id>`. They are removed on null, as the existing keys are.
  - Keep `subtitle_lang_`/`subtitle_url_` as they are. Old entries then read as a preference with `isForced = null` and `descriptiveLabel = null`, which the resolver treats as "don't care". That makes the old-to-new compatibility automatic.
- Save (`PlayerVM.kt:1849`):
  - Save from `intendedSubtitleRow`. The URL slot uses `preferenceKey()`: the API url, then sourceFile, then the merge-prefix-stripped manifest group id (only if `fromManifest`), then the rendition URI, then the track id.
  - Also save forced and `readableDescriptiveLabel()`. The pass-through while the restore is pending covers all four values.
- Resolver (`findSubtitleTrackIndex`):
  - Upstream's semantic pre-filter (language, forced, readable label).
  - Identity over all of a row's ids.
  - Descriptive-label match.
  - Language match that prefers manifest rows.
  - The f9c9ff38 empty-language rule.
  - Coordinates stay out, since the fork dropped positional matching.
- Optionally give `findSameSubtitleTrackIndex` the same label compatibility.
- Rebuild restore: resolve from `intendedSubtitleRow` first, then fall back to the saved preference (upstream `previousSubtitle ?: saved`). That closes G3 on a rebuild.
- Tests:
  - Repository round trip.
  - Resolver cases ported from 7d5addd2 and f9c9ff38.
  - Adapted `PlayerVMSubtitleRestoreTest` scenarios.
  - The option A test.
- Pros: fixes G1-G3 with upstream-proven rules. Mostly pure functions, unit-testable. No controller contract change.
- Cons: it changes a persisted data shape (a risk area). It also needs a device check of exit and re-entry on HLS.

**C. B plus the controller half of 7d5addd2 and the instrumented cases (M to L).** On top of B:
- `switchStream` disables text and clears overrides before replacing the source (upstream `disableSubtitlesForSourceReplacement`).
- Port the instrumented `PlaybackControllerNetworkTracksTest` cases.
- Pros: matches upstream fully, and avoids a possible flash of the wrong track during a quality switch.
- Cons:
  - The flash is `unverified` on the fork.
  - It changes the D19 mirror semantics.
  - It overlaps `playback-controller-split`.
  - It depends on P6 `:player-test-fixtures`, which is not on master.

**Recommended: B.** Leave C's controller change and instrumented tests as a note on `playback-controller-split` and P6.

## 7. Risks

- **Persisted shape.** New keys must be removed on Off, or a stale forced flag or label would filter out every row later. When an explicit Off is chosen, all four values must clear together (upstream test `subtitlePreferenceMetadata_persistsAndClearsSemanticIdentity`).
- **Storing a non-URL in `subtitle_url_`.** If the group id is stored with its `0:` prefix, it stops matching once the API subtitle set changes. Strip it, and never store side-load group ids (`"<n>:0"`). `stableSubtitleKey()` and `sameSubtitleIdentity` on a value like `subtitle:Russian` fall back to plain equality (`SubtitleTrackIdentity.kt:13-31`). That is safe but worth one test.
- **Over-strict semantic filter.** A saved readable label that KinoPub renames between sessions would filter out the right row and give Off. Upstream has the same behaviour. Whether KinoPub manifest NAMEs are stable is `unverified`.
- **Intent first on rebuild** changes which value wins when the intent and the saved preference differ. Today they differ only after a `persist=false` restore, so the risk is low, but the existing tests at `PlayerVMTrackSelectionTest.kt:401-457` must stay green.
- **Device evidence** needs an HLS title with a manifest-only subtitle, ideally same-language full and forced, or a language-less rendition. Earlier runs never found a forced title (sideload-merge AC-2/5/9 NOT CHECKED). Psyren S1E1 and Outlander (manifest-only rows) can at least show exit and re-entry.
- **Conflicts** with `player-vm-split` (`PlayerVM` save and restore) and with P6 if it lands first and touches `PlaybackController`.

## 8. Open questions

1. **Is the deferred audio-less rebuild item closed by `c6ea3789` and `871b06a6`?** Recommended: yes. Add the single regression test (option A's test) inside this run and record it as closed. Do not change `prepare()`.
2. **Where does a manifest-only row's identity go: upstream's way (in `subtitle_url_` via `preferenceKey()`) or a new key?** Recommended: upstream's way, with the merge prefix stripped. That means fewer keys, upstream tests port with small changes, and the resolver already matches any identity against that slot.
3. **On a rebuild, should the live intent beat the saved preference?** Recommended: yes, intent first, then saved (upstream `previousSubtitle ?: saved`). This closes the forced-replaces-full case on a rebuild.
4. **Include `switchStream`'s text disable and the instrumented tests (option C)?** Recommended: no. Note them on `playback-controller-split` and P6, unless the device check shows a wrong track during a quality switch.
5. **Device check scope?** Recommended: exit and re-enter on an HLS title with a manifest-only row (Psyren or Outlander) after picking it, then after a quality switch. Add a forced-track title only if the owner can name one. Forced and language-less cases otherwise rest on unit tests.

## 9. Sources

- Files (master `f351d988`):
  - `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt`, `PlayerVM.kt`, `AudioTrackPreferenceResolver.kt`, `SubtitleTrackSelector.kt`, `SubtitleTrackMerger.kt`, `PlayerTextTracks.kt`, `SubtitleReportKind.kt`, `SubtitleTrackIdentity.kt`, `PlaybackMediaItemFactory.kt`
  - `ui/feature/player/model/PlayerUIModels.kt`, `SubtitleLabeler.kt`
  - `data/repository/PlayerPreferencesRepository.kt`
  - `domain/interactor/player/PlayerInteractor.kt`
  - `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`
  - `docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md`
  - `AGENTS.md`, `docs/architecture/` (no subtitle content)
- Fork commits: `c6ea3789`, `36da8957`, `f4b311e0`, `5a4b89e9`, `871b06a6`, `6fcaddf0`, `50f46021`.
- Upstream commits: `a563d6f6`, `6123fdbc`, `7d5addd2`, `f9c9ff38`, HEAD `b755826c` (`PlayerVM.kt`, `AudioTrackPreferenceResolver.kt`, `PlayerVMSubtitleRestoreTest.kt`).
- Runs:
  - `subtitle-sideload-merge`: state, research, design-architect-2, triage-3, spec.
  - `subtitle-upstream-port`: state, triage-2.
  - `playback-controller-split`, `player-vm-split`, `upstream-sync-triage` (state, spec D24, from the worktree), `manual-quality-hls4`.
- URLs:
  - https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaPeriod.java
  - https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MergingMediaPeriod.java
