# Brief: subtitle-sideload-merge

Input: `research.md` (cited as `research.md: <section>`). No brief template path was given; this follows the usual brief sections.

## Request

"Port the rest of upstream's subtitle work: side-load API subtitles on HLS (003d4c55) and the full subtitle merge model; starts after subtitle-upstream-port lands."

## Classification

- kind: feature
- size: L as asked (side-load + merge model + restore-across-recreation with new pref keys is more than one shippable goal). Recommended scope of THIS run after the split: step 1, size M.
- tracks: none. The picker UI change is label text only (`PlayerSettingsPanel.kt:231-252` unchanged structurally; research.md: 3). Owner may add `ui` if label wording is to be judged.
- on-device verification: YES (playback, networking, ViewModel state, persistence in step 2). Needs a KinoPub HLS title whose API list differs from its manifest (research.md: 7).
- Hard prerequisite: `subtitle-upstream-port` merged to master (it edits the same lines: `PlaybackController` text-track code, `PlayerUIMapper`, `Models.kt`, `SubtitleLabels.kt`). Do not start before.

## Findings (code wins)

- Root cause of the HLS gap, confirmed: `PlaybackController.kt:651-675` builds HLS with `HlsMediaSource.Factory(...).createMediaSource(mediaItem)`. That factory never reads `MediaItem.subtitleConfigurations` (Media3 1.11.0 source, research.md: 5), so the API subtitles attached at `:599-614` are dropped on HLS. Only progressive (`player.setMediaItem`) side-loads them via `DefaultMediaSourceFactory`. Present since `88cdf071`.
- The VM never sees player text tracks: `PlaybackControl.Callback.onTracksUpdated(audioTracks, selectedIndex)` is audio only (`PlaybackController.kt:67`, impl `PlayerVM.kt:211`). The picker comes from the API list only (`PlayerUIMapper.kt:35-75`).
- `MergingMediaSource` rewrites `format.id` / group id to `"<n>:<id>"`, so after side-loading, matching by `format.id == stableKey` (`PlaybackController.kt:866-942`) fails and only the label match works. Matching and selection must strip the prefix (research.md: 5).
- Warm-up player builds HLS with the same shape (`PlaybackController.kt:748-764`); whether it must change for cache hits is unverified.
- Missing from master: `SubtitleTrackMerger`, `SubtitleTrackSelector`, `SubtitleLabeler`, `SubtitleLanguage`, `PlaybackMediaItemFactory` (only `SubtitleTrackIdentity.kt` `stableSubtitleKey()` exists).
- Fork already has `StreamType` on `StreamCandidate` (`PlayerInteractor.kt:19-26,232-245`), so upstream f38fbcf0 / dc011a81 are not needed.

## Options (research.md: 6)

- A. Side-load only (S). Rejected: duplicates every language the manifest has, manifest-only subtitles stay invisible; upstream abandoned this state within a day.
- B. Whole upstream final model in one run (L). Rejected: two goals, widest conflict surface.
- C. Two steps (M + M). **Recommended.**
  - Step 1 (this run): side-load HLS through `DefaultMediaSourceFactory` plus `PlaybackMediaItemFactory`; report player text tracks to the VM; extend `SubtitleTrackUIState` with player identity fields; `SubtitleTrackMerger` (rule 9bd835b0); `SubtitleTrackSelector` (select by merge-stable group id, id prefix stripped); `SubtitleLabeler`/`SubtitleLanguage` (reconciled with `SubtitleLabels.kt`); existing lang+URL restore kept via the resolver.
  - Step 2 (follow-up run, to be created at the end of step 1): restore across playback recreation, forced and language-less selection, `SubtitlePreference` with new forced/descriptive-label pref keys (7d5addd2, f9c9ff38, restore part of 6123fdbc, a563d6f6), old-to-new pref compatibility.
- Port from upstream HEAD `b755826c` state, not by replaying the 28 commits.

## SPLIT proposal

1. M - `subtitle-sideload-merge` (this run, step 1): side-load + player text tracks to VM + merger + selector + labeler, existing restore kept. Start after `subtitle-upstream-port` is on master.
2. M - `subtitle-restore-model` (new run, step 2): `SubtitlePreference`, restore across recreation, forced/language-less selection, pref-key migration. After 1.
3. Sequencing: this run before `playback-controller-split` (which then drops its text-track extraction, SOLID-4) and before `player-vm-split`; coordinate with `upstream-sync-triage` P6 (instrumented fixtures come after).

## Acceptance criteria (step 1)

AC-1. Given an HLS stream whose API subtitle list contains a language the manifest does not declare, When playback starts, Then that subtitle appears in the picker and can be selected and rendered. (device)
AC-2. Given an HLS stream whose manifest declares a language that the API list lacks, When the picker opens, Then the manifest subtitle is listed with a language-name label and is selectable. (device; unit for the merger)
AC-3. Given the same language offered by both manifest and API, When rows are merged, Then exactly one source is kept per language: the one with more tracks, HLS on a tie; rows with unknown language are kept unless proven duplicates. (unit: `SubtitleTrackMergerTest`)
AC-4. Given side-loaded tracks whose ids carry the `n:` prefix from `MergingMediaSource`, When the user selects a row, Then the matching text group is selected by the prefix-stripped, merge-stable group id, not by position. (unit: `SubtitleTrackSelectorTest`)
AC-5. Given progressive streams, When playback starts, Then subtitle behaviour is unchanged (still side-loaded, Off by default, rows as before). (unit + device)
AC-6. Given the player reports text tracks, When tracks change (switch stream, rebuild), Then the picker rows and the selected row update through `PlaybackControl.Callback` without losing the user's selection when the same merged row still exists. (unit: `PlayerVM` tests)
AC-7. Given a stored per-item subtitle language/URL preference, When the item opens, Then the same restore as on master still selects the matching merged row (existing resolver behaviour kept). (unit, extend `PlayerVMTrackSelectionTest`)
AC-8. Given duplicate languages across rows, When labels render, Then labels use language names with AI/forced qualifiers and disambiguating ordinals consistent with the first batch's strings (no raw ids). (unit: `SubtitleLabelerTest`; wording `(owner judges)`)
AC-9. Given a side-loaded subtitle URL that fails to load (404/TLS), When the stream plays, Then playback continues without a fatal error and the video is unaffected. (unit with fake/test source or device check; currently unverified, see risks)
AC-10. Given the warm-up player and the main player, When HLS starts, Then time-to-first-frame is not regressed and the warm cache is still hit. (device observation; only if warm-up shape changes)
AC-11. Given the change, Then `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` passes and new unit tests (merger, selector, labeler, VM merge) are JUnit 5 with `FakeResourceProvider` where strings are mapped.

## Risks

- Prerequisite churn: `subtitle-upstream-port` is uncommitted in its worktree; starting early causes rebase conflicts.
- Merged id prefix, and child order may change on source rebuild (`switchStream`, fallback).
- Side-loaded load failure may become a fatal error under `PlaybackErrorPolicy` (unverified); affects CDN fallback and network diagnostics.
- Extra subtitle fetches per start through the shared cache; unselected side-loads should not download (unverified).
- Warm-up player source shape (unverified).
- Risk areas touched: playback, ViewModel state, networking. Step 2 adds persistence (data shape).
- Device evidence needs an authenticated emulator and a suitable title; hermetic tests depend on `upstream-sync-triage` P6.
- Conflicts with `playback-controller-split`, `player-vm-split`, P6.

## Open questions (asked to the owner; at most 3)

1. Scope: one run (B) or two steps (C)? Recommended: C, this run is step 1 (side-load + merge + selector + labeler), restore-across-recreation with new pref keys is a follow-up run. Blocks: yes.
2. Which labeler survives: first batch's `SubtitleLabels.kt` or upstream's `SubtitleLabeler`/`SubtitleLanguage`? Recommended: upstream's, fed with the first batch's strings and its Russian ISO-3 rule, so labels cover merged player rows. Blocks: yes (AC-8, files touched).
3. Merge rule when manifest and API both offer a language? Recommended: upstream 9bd835b0 (more tracks wins, HLS on tie, keep unknown-language rows unless proven duplicates). Blocks: yes (AC-3).

## Assumed (not asked)

- Port the final upstream state at `b755826c`, not the 28 commits one by one.
- Order: this run before `playback-controller-split`; that run drops its text-track half and keeps debug info and the package move.
- Test title for the device check: owner supplies an HLS title id whose API list differs from its manifest plus one with a forced track; asked again at the device step, not blocking now.

## Size note

This is L as requested; no Plan is written. Implement step 1 as an M after the owner confirms the split.
