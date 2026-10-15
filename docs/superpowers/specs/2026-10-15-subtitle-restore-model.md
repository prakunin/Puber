# Persist and restore the chosen subtitle across player recreation

Status: approved
Date: 2026-10-15
Slug: subtitle-restore-model
Kind: improvement
Size: M
Tracks: none

## Problem / Why

<!-- owner: analyst (brief.md) -->

Request: "Restore the chosen subtitle across player recreation with upstream's SubtitlePreference keys (7d5addd2, f9c9ff38, a563d6f6)".

- Deferred audio-less rebuild item: **closed in code, confirmed** (`research.md: 3`). `PlaybackController.kt:822` returns early only when there are neither audio nor text groups; `PlayerVM.kt:552-557` keeps audio rows on an audio-empty report; `PlayerVM.kt:927-951` keeps `intendedSubtitleRow` on rebuild (`871b06a6`). `prepare()` clearing `pendingSubtitleTrack` (`PlaybackController.kt:385-387`) is harmless because the VM re-sends. Only a regression test is missing. Decision: closed, pin it with one test, do not touch `prepare()`.
- Persisted shape today: `subtitle_lang_<id>` and `subtitle_url_<id>` only (`data/repository/PlayerPreferencesRepository.kt:16-22,28-56,119-122`; `domain/interactor/player/PlayerInteractor.kt:335-358`).
- Save: `PlayerVM.kt:1849-1871` writes `intendedSubtitleRow.language` / `.url`, each only when non-empty.
  - G1 (confirmed by reading): a manifest-only row without language has `url=""`, so both values are null and both keys are removed, i.e. the pick is stored as Off.
  - G2 (confirmed): a manifest-only row with a language saves only the language.
- Restore: `PlayerVM.kt:599-616` resolves only the saved preference; `AudioTrackPreferenceResolver.kt:26-39` has three rules (exact URL, stable key, unambiguous language that ignores forced and labels).
  - G2 (continued, confirmed): two same-language rows (full and forced, or labelled variants) are ambiguous, so the result is Off.
  - G3 (confirmed by reading, no test): when only a forced row of that language exists, the language rule picks it with `persist=false` (`PlayerVM.kt:606-608`), so a full-subtitle viewer silently gets the forced track.
- Rebuild order today: saved preference first, intent is only a fallback. Upstream order is `previousSubtitle ?: saved`.
- Already on master, do not re-port: a563d6f6 (separate audio/subtitle restore, pass-through while pending), selector semantic filter and `isForced` (7d5addd2 part), FORCED flag on side-loads and language-less selector identity (f9c9ff38 part), see `research.md: 3`.
- Media3 1.11.0 ids (`research.md: 5`): a manifest group id is `subtitle:<NAME>`, or `0:subtitle:<NAME>` when the item has side-loads. The prefix must be stripped before persisting (`withoutMergedSourcePrefix` in `SubtitleTrackIdentity.kt`); side-load ids `<n>:0` are positional and must never be persisted.

### Scope (option B)

1. `SubtitlePreference(language, url, isForced, descriptiveLabel)`; repository keys `subtitle_forced_<id>` (Boolean, null when the key is missing) and `subtitle_label_<id>`; interactor getter returning the preference. Old `subtitle_lang_`/`subtitle_url_` entries keep working (forced and label null = don't care).
2. Save from `intendedSubtitleRow` with a `preferenceKey()` in the URL slot: url, sourceFile, merge-prefix-stripped manifest group id (manifest rows only), rendition URI, track id. Also save forced and the readable descriptive label. Pass-through while the restore is pending covers all four values. An explicit Off clears all four.
3. Resolver `findSubtitleTrackIndex`: semantic pre-filter (language, forced, readable label), identity over all row ids, descriptive-label match, language match that prefers manifest rows, empty preferred language means Off only when no identity is given. No coordinates.
4. Rebuild restore: intent first, then the saved preference.
5. Tests (below).

## Acceptance criteria

<!-- owner: analyst (brief.md); this run is Part 1, AC-1..AC-12 -->

- AC-1. Given a manifest-only row with a language and no API URL is picked, When the preference is saved, Then the group id with the `N:` merge prefix stripped is stored in `subtitle_url_`, together with language, forced flag and label, and a restore in a new session picks that row. (unit: VM + repository)
- AC-2. Given a language-less manifest-only row is picked, When saved, Then the preference is not stored as Off: the identity key is persisted, and after re-entry the same row is selected. (unit)
- AC-3. Given the viewer picked Off explicitly, When saved, Then `subtitle_lang_`, `subtitle_url_`, `subtitle_forced_` and `subtitle_label_` are all removed (no stale forced flag or label). (unit, repository)
- AC-4. Given a saved preference from before this change (only lang and url), When it is read and restored, Then every old preference that resolved to a row resolves to the same row, and the existing resolver tests pass unchanged; a formerly ambiguous language may now resolve to the single manifest row. (unit, resolver and repository) _(reworded at gate 1, Q1)_
- AC-5. Given the saved preference is a full subtitle and the stream has only a forced row of that language, When restoring, Then the result is Off (no match), not the forced row. (unit, resolver and VM)
- AC-6. Given two rows share a language (full and forced, or two labelled variants), When restoring, Then the saved forced flag and readable label choose the right one instead of giving Off. (unit, resolver)
- AC-7. Given a preference carrying an identity (language-less) whose row has disappeared, When restoring, Then it does not select Off by the empty-language rule. Given a preference with an empty language and no identity, Then it still means Off. (unit, resolver)
- AC-8. Given the player is rebuilt (buffer preset or Fast DNS) and the live intent differs from the saved preference, When the subtitle restore runs, Then the intent is resolved first and the saved preference is the fallback. Existing tests `PlayerVMTrackSelectionTest.kt:401-457` stay green. (unit)
- AC-9. Given a rebuild followed by an audio-less report that contains text groups, When tracks are reported, Then the intended subtitle is re-sent to the controller and nothing is persisted. This closes the deferred audio-less item. (unit, regression)
- AC-10. Given a side-loaded row (group id `<n>:0`), When saved, Then its positional group id is never written to `subtitle_url_`. (unit)
- AC-11. Given an HLS title with a manifest-only subtitle, When the viewer picks it, leaves the player and opens the same title again, Then the same subtitle is on. After a quality switch it is on when the new stream offers that row; on a stream without it the picker shows Off without saving Off, and the pick comes back on Auto and on re-entry. (device, D-pad) _(reworded at gate 1, Q4: manual quality plays url.hls, see run manual-quality-hls4)_
- AC-12. `check.full` passes: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (Part 1). (Part 2's instrumentation compile moved to run subtitle-switch-text-disable.)
- AC-16. Given a restore found no row for the saved preference and the viewer has not picked a subtitle in this media, When preferences are saved (e.g. on an audio pick), Then the stored subtitle preference is kept, not overwritten with Off. (unit: T-16a..c) _(added at gate 1, Q2)_

## Out of scope

<!-- owner: analyst (brief.md) -->

- Building `:player-test-fixtures` (that is P6-A, owned by upstream-sync-triage), Kaspresso, Robolectric.
- Upstream's callback-generation gate (`PlaybackCallbackGate`, P6 D25/D26).
- Any change to `prepare()`, the D19 mirror semantics, the merger, or the selector.
- Audio preference persistence, UI layout, strings.
- Extracting coordinators from `PlayerVM` (`player-vm-split`).
- Option C (switchStream text disable, instrumented tests, AC-13..AC-15): split to run `subtitle-switch-text-disable` after P6-A (owner, 2026-10-15).

## Design

<!-- owner: architect (design-architect.md) -->

Base `f351d988`, worktree `/home/user1/orca/workspaces/Puber/subtitle-restore-model`. Scope: option B, AC-1..AC-12
(AC-12 = `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`, no instrumentation compile).
This builds on the sideload-merge model (spec `docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md`
D11 amended, D17-D20). The VM owns `intendedSubtitleRow`, the restore waits for text, and the controller's
pending row is only a mirror. That model is unchanged. Upstream 7d5addd2 / f9c9ff38 is ported by intent, not
line by line. App paths below are relative to `app/src/main/java/com/kino/puber/`, test paths to
`app/src/test/kotlin/com/kino/puber/`. Every `path:line` was read at `f351d988`.

### Decisions

| ID | Question | Decision | Why |
|----|----------|----------|-----|
| D1 | Where does `SubtitlePreference` live? | New `domain/model/SubtitlePreference.kt`: `data class SubtitlePreference(val language: String?, val url: String?, val isForced: Boolean?, val descriptiveLabel: String?)` with `companion object { val NONE = SubtitlePreference(null, null, null, null) }`. All four nullable; null means "not saved / don't care". | The repository already imports `domain.model` (`data/repository/PlayerPreferencesRepository.kt:5-6`). The resolver and VM consume it. Upstream declared it in the repository file, which would make `ui` import from `data`. |
| D2 | Persisted shape and old-to-new compatibility | Keep `subtitle_lang_<id>` and `subtitle_url_<id>` exactly as they are (`PlayerPreferencesRepository.kt:121-122`). Add `subtitle_forced_<id>` (Boolean) and `subtitle_label_<id>` (String). Read forced through `prefs.contains(key)`, so a missing key gives `null`. Add one reader, `getPreferredSubtitlePreference(itemId): SubtitlePreference`. `saveTrackPreferences` writes all four subtitle values in the existing single `prefs.edit {}` block (`:35-56`), and each null **removes** its key. The public `getPreferredSubtitleLang`/`Url` (`:16-22`) go away; their key reads are inlined into the new reader. | Old entries read as `(lang, url, null, null)`. The resolver treats null forced and label as "don't care" (D6), so they resolve as before (AC-4). No migration and no version key are needed. The new keys share the edit transaction, and a null removes the key, so an Off cannot leave a stale forced flag or label behind (AC-3). |
| D3 | Interactor and save signature | `domain/interactor/player/PlayerInteractor.kt:335-342` turns the two getters into `getPreferredSubtitlePreference(itemId): SubtitlePreference`. `saveTrackPreferences` (`:343-358`) becomes `(itemId, audioLang, audioLabel, subtitle: SubtitlePreference)` on both the interactor and the repository. **No default parameters.** | Upstream added `subtitleIsForced = null, subtitleDescriptiveLabel = null` defaults. With mockk, `verify { saveTrackPreferences(any(), any(), any(), any(), any()) }` would then fill the defaults with `eq(null)` and silently stop matching real saves. 47 such calls exist in `ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`, plus the stubs at `PlayerVMTestFixture.kt:107-108,116`. A signature that breaks compilation forces each one to be migrated on purpose. |
| D4 | What goes into `subtitle_url_` (the preference key) | Add a new file `ui/feature/player/vm/SubtitlePreferenceKey.kt` with `internal fun SubtitleTrackUIState.subtitlePreferenceKey(): String?`. It returns the first non-empty value of: `url`, then `sourceFile`, then `playerTrackGroupId.withoutMergedSourcePrefix()` **only when `fromManifest`**, then `playerTrackUri`, then `playerTrackId`. If all are empty it returns null, never `""`. The same file holds `internal fun SubtitleTrackUIState?.toSubtitlePreference(): SubtitlePreference`. Off or null gives `NONE`. Otherwise it gives `language.takeIf { it.isNotEmpty() }`, `subtitlePreferenceKey()`, `isForced` and `readableDescriptiveLabel()` (`ui/feature/player/model/SubtitleLabeler.kt:93-97`). | This covers G1 and G2, the identity of a manifest-only row (AC-1, AC-2). Media3 1.11.0 names a rendition group `subtitle:<NAME>`, which is stable across variants. `MergingMediaPeriod` prefixes it with `0:` only when the item has side-loads, so the strip (`vm/SubtitleTrackIdentity.kt:50-53`) makes the stored key independent of the API list. A side-load's group id is `<n>:0` (positional), so it is gated out by `fromManifest`. Its `playerTrackId` is the URL's stable key (`vm/PlaybackMediaItemFactory.kt:45,51`), which is safe to store, and its `url` always comes first anyway (AC-10). A language-less row stores language null, never `""` (see D6 rule 0). |
| D5 | Which row values count as identity when restoring | In `SubtitlePreferenceKey.kt`: `internal val SubtitleTrackUIState.preferenceIdentities`. It is the non-empty values among `url`, `sourceFile`, `playerTrackUri`, `playerTrackId`, plus `playerTrackGroupId` and `playerTrackGroupId.withoutMergedSourcePrefix()` for `fromManifest` rows only. Comparison uses `sameSubtitleIdentity` (`SubtitleTrackIdentity.kt:13-23`). | Saving and matching share one definition. `sameSubtitleIdentity("subtitle:X", "subtitle:X")` holds by plain equality (`:14`). A group id has no `/` and no subtitle extension, so `subtitleIdentityPathOrNull` returns null (`:25-31`) and no path rule can make it collide with a URL. Positional side-load group ids are never compared. |
| D6 | Resolver rules (`findSubtitleTrackIndex`, `vm/AudioTrackPreferenceResolver.kt:26-39`) | New signature: `findSubtitleTrackIndex(tracks, preferredLang, preferredUrl, preferredIsForced: Boolean? = null, preferredDescriptiveLabel: String? = null)`. The rules run in order; the first that names a row wins. **0.** If `preferredLang == ""` and `preferredUrl` is null or empty, return the Off index (f9c9ff38). **1.** Exact `url == preferredUrl`, over **all** rows, first index (today's `:98-104`, unchanged). **2.** Stable key of `url`, over all rows, first index (today's `:106-112`, unchanged). Then a semantic pre-filter (D7) narrows the candidates. **3.** The single candidate whose `preferenceIdentities` contain `preferredUrl`. **4.** The single candidate whose normalised `readableDescriptiveLabel()` (trim, lowercase) equals the preference's. **5.** Language: the single `fromManifest` candidate with `sameSubtitleLanguage`, else the single candidate with it (empty language gives no match). Otherwise `NO_MATCH`. No coordinate rule exists. | Rules 1 and 2 stay first and unfiltered. Every outcome the old resolver produced through a URL is therefore preserved exactly, and the existing tests (`AudioTrackPreferenceResolverTest.kt:115-126`) run unchanged (AC-4). Rules 3-5 are upstream's identity, label and language matches over semantically compatible rows (AC-1, AC-2, AC-5, AC-6). The manifest preference uses `fromManifest`, not upstream's `playerTrackId != null`, because in the fork side-loads also carry a `playerTrackId` (`AudioTrackPreferenceResolverTest.kt:13-19`). Rule 0 together with the stored language never being `""` (D4) gives AC-7: a language-less identity that is gone ends at `NO_MATCH`, not at an Off pick. |
| D7 | Semantic filter strictness | A row is compatible when all three hold. **Language:** preferred null or empty, **or** the row language is blank, **or** `sameSubtitleLanguage`. **Forced:** preferred null, **or** row `isForced` null, **or** equal. **Label:** preferred null, **or** the row has no readable label, **or** the normalised labels are equal. Off rows are always compatible (no rule can pick them except rule 0). | Upstream is strict on the row side (`isForced == preferredIsForced`, `label == preferredLabel`). In the fork, API rows carry `forced` from a nullable field (`data/api/models/Models.kt:434`, `ui/feature/player/model/PlayerUIMapper.kt:47`). Side-load and API rows never have a readable label: the side-load label is the file key (`PlaybackMediaItemFactory.kt:52`), which `SubtitleLabeler.kt:100-105` rejects. Under the strict rule, a preference saved from a labelled HLS row would become Off on a progressive stream or on a CDN fallback to progressive. The tolerant rule matches the fork's in-session rule (`AudioTrackPreferenceResolver.kt:87-96`). AC-5 still holds: a manifest forced row has a definite `true` (`vm/PlayerTextTracks.kt:48`) and is filtered out by a saved `false`. |
| D8 | Save (`PlayerVM.kt:1849-1870`) | Once the restore is done (`subtitleRestoredForCurrentMedia`), save `intendedSubtitleRow.toSubtitlePreference()`. While it is pending, save `interactor.getPreferredSubtitlePreference(params.itemId)` back unchanged (all four values pass through). An explicit Off sets the intent to null (`:875`), so `NONE` is written and all four keys are removed. | This is the existing pass-through rule, widened to four values (brief scope 2, AC-3). No new VM function is needed. |
| D9 | Rebuild restore order (AC-8) | In `restoreSubtitlePreference` (`PlayerVM.kt:599-616`), first check `intendedSubtitleRow != null` and the displayed row, `content.subtitleTracks[content.selectedSubtitleIndex]`, is not Off. In that case `onTracksUpdated` has already re-found the intent (`:545-550`): re-send that row with `playbackController.selectSubtitle(row)`, do not persist, and return. Otherwise resolve the saved preference with D6, passing all four fields. If it matches, call `applySubtitleSelection(index, persist = false)` (today's `:608-610`). If not, re-send the displayed row (today's `:612-615`). | The intent is resolved first, by the in-session re-finder that already exists (`findSameSubtitleTrackIndex`, `:48-65`). The saved preference is the fallback. On new media the intent is null (`resetMediaSession`, `:995`), so the path is the saved restore alone. On a rebuild (`:936-937`, `:948-949`) the intent is kept, so it wins. The existing rebuild tests (`PlayerVMTrackSelectionTest.kt:401-457`) expect exactly one `selectSubtitle` for the picked row and no save. This branch produces that. |
| D10 | Deferred audio-less rebuild item | No production change. `PlaybackController.kt:822` reports when there are text groups even if audio is empty. `PlayerVM.kt:553-558` keeps the audio rows. `restoreAudioPreference` (`:584-597`) is a no-op on an empty audio list. D9 re-sends the intent. AC-9 adds one VM regression test: a Fast DNS rebuild, then a report with **empty audio** and text rows, then the assertion of one `selectSubtitle(pick)` and zero saves. | Brief finding 1; `research.md: 3`. `prepare()` (`PlaybackController.kt:385-387`) stays untouched (out of scope). |
| D11 | In-session re-find (`findSameSubtitleTrackIndex`) | Unchanged: it gets no label rule. | No AC needs it. Quality switches already re-find manifest rows by full group id plus index (`:59`, `:80-84`), and that id is the same across variants of one item. |
| D12 | DI, screen, Compose, resources | None. | Resolver and helpers are plain functions or fields (`PlayerVM.kt:180`). The Koin scope, UI and strings are unchanged. |

### Changes by layer

#### API / repository

- New `domain/model/SubtitlePreference.kt` (D1).
- `data/repository/PlayerPreferencesRepository.kt`:
  - `:16-22`: replace the two public getters with `getPreferredSubtitlePreference(itemId)`. It reads lang and url with `getString`, forced with `contains` plus `getBoolean`, and label with `getString` (D2).
  - `:28-56` `saveTrackPreferences(itemId, audioLang, audioLabel, subtitle: SubtitlePreference)`: the four subtitle keys are put or removed in the same `edit {}` (D2, D3).
  - `:121-122`: add `KEY_SUBTITLE_FORCED_PREFIX = "subtitle_forced_"` and `KEY_SUBTITLE_LABEL_PREFIX = "subtitle_label_"`.
- `domain/interactor/player/PlayerInteractor.kt:335-358`: one getter and the new save signature, both delegating (D3).
- API models and `KinoPubApiClient`: unchanged.

#### ViewModel state and actions

- New `ui/feature/player/vm/SubtitlePreferenceKey.kt`: `subtitlePreferenceKey()`, `preferenceIdentities` and `toSubtitlePreference()` (D4, D5). It uses `withoutMergedSourcePrefix` (`SubtitleTrackIdentity.kt:51`), `sameSubtitleIdentity` (`:13`) and `readableDescriptiveLabel` (`model/SubtitleLabeler.kt:93`).
- `ui/feature/player/vm/AudioTrackPreferenceResolver.kt`:
  - `:26-39`: the new signature and rule order 0-5 (D6).
  - New private helpers: compatibility filter (D7), identity match, label match, and a manifest-preferring language match.
  - `:151-158` `unambiguousSubtitleLanguageMatch` is replaced by the manifest-preferring match.
  - `:98-112` stays as rules 1 and 2.
  - `findSameSubtitleTrackIndex` (`:48-65`) and the audio matchers are untouched.
- `ui/feature/player/vm/PlayerVM.kt`:
  - `:599-616` `restoreSubtitlePreference`: intent-first branch, then the saved `SubtitlePreference` through the resolver (D9).
  - `:1849-1870` `saveTrackPreferences`: `toSubtitlePreference()` or the stored preference passed through, under the new interactor signature (D8).
  - No new functions and no new fields. `onTracksUpdated` (`:535-579`), `applySubtitleSelection` (`:869-882`) and the rebuild entry points (`:927-951`) are unchanged.
- `PlaybackController`: unchanged (Part 2 owns `switchStream`).

#### Screen and DI module

- None (D12).

#### Compose content

- None.

#### Resources

- None.

#### Tests (inputs for the test-designer; existing files)

- `data/repository/PlayerPreferencesRepositoryTest.kt` (fixture `:33-36` on `util/FakeSharedPreferences.kt`, which supports `contains`, `getBoolean`, `putBoolean` and `remove`, `:31-55`):
  - A round trip of all four values.
  - Explicit `NONE` clears all four (AC-3).
  - Old keys alone read as `(lang, url, null, null)` (AC-4).
- `ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt`:
  - `:115-126` stay unchanged (AC-4).
  - Add the ported cases: `rejectsForcedOnlyReplacementForFullPreference` (AC-5), `usesMeaningfulLabelToResolveSameLanguageVariants` and the forced/full pair (AC-6), `preservesLanguageLessTrackByIdentity` (AC-2), `doesNotRestoreOffWhenLanguageLessIdentityDisappears` plus empty language without identity gives Off (AC-7).
  - Also: a merge-prefixed group id matches a stored unprefixed one (AC-1), and a positional side-load group id never matches (AC-10).
- New `ui/feature/player/vm/SubtitlePreferenceKeyTest.kt`:
  - The key order.
  - Prefix strip for manifest rows.
  - A side-load gives url or stable key, never `<n>:0` (AC-10).
  - A language-less row gives language null.
  - Off gives `NONE`.
- `ui/feature/player/vm/PlayerVMTrackSelectionTest.kt` and `PlayerVMTestFixture.kt:107-108,116`:
  - Migrate the stubs and verifies to the new signatures (D3).
  - Add VM cases for AC-1, AC-2, AC-5 and AC-8 (intent differs from saved), and AC-9 (D10).
  - `:401-457` stay green.

### Alternatives rejected

- A new key `subtitle_track_<id>` for the manifest identity: the URL slot already holds identities and upstream's tests assume it, and an extra key would be one more thing to clear on Off.
- Persisting the full, prefixed group id: it changes whenever the API subtitle list appears or disappears (`0:` prefix), so restores would silently fail.
- Upstream's strict forced and label filter: it turns URL-saved preferences into Off on progressive or fallback streams in the fork (D7).
- Running the identity rules only over filtered rows, including URL equality: this changes old-preference outcomes, which AC-4 forbids.
- Adding a fourth and fifth default parameter to `saveTrackPreferences` (upstream): with mockk this silently weakens 47 verifies (D3).
- A schema version or migration pass: old entries are already a valid subset (D2).
- Resolving the intent through `findSubtitleTrackIndex` (upstream `previousSubtitle ?: saved` field by field): `findSameSubtitleTrackIndex` already re-finds it with more information (group id plus index, player track id), so re-resolving would be weaker and duplicated.
- A label rule in `findSameSubtitleTrackIndex`: no AC needs it (D11).
- Changing `prepare()` to keep `pendingSubtitleTrack`: out of scope, and not needed (D10).

### Risks (highest first)

1. **Persisted data shape: stale or partial keys** (correctness, data shape). A forced flag or label left behind after Off would filter out every row later. It lives in `PlayerPreferencesRepository.saveTrackPreferences` (`:28-56`). Mitigation: all four keys are written in one `edit {}`, and a null removes the key. The repository test pins clearing (AC-3).
2. **Wrong row from an identity collision** (correctness). A stored `subtitle:<NAME>` might equal some other row's id. It lives in the D5 identities and D6 rule 3. Mitigation:
   - Rule 3 must name exactly one row.
   - Positional side-load group ids are excluded.
   - Equality is the only path for non-path ids (`SubtitleTrackIdentity.kt:14,25-31`).
   - Manifest `playerTrackId` is `<GROUP-ID>:<NAME>` and the group id is `subtitle:<NAME>`, so they differ unless the GROUP-ID is literally `subtitle`, in which case they name the same rendition anyway.
3. **Saved preference wiped after a restore that found nothing** (data loss, pre-existing). A fresh session on a stream without the saved row (the AC-5 forced-only case, or a quality lacking the row) leaves the intent null. The next audio pick then saves `NONE` (`PlayerVM.kt:1856-1858`, `:865`), so the full-subtitle preference is gone for the whole series. Not asked for: see Q2.
4. **Over-strict label filter** (correctness). If KinoPub renames a manifest NAME between sessions, the right row is filtered out and the restore gives Off. It lives in the D7 label clause. Mitigation: rows without a readable label are tolerated, and URL rules 1-2 bypass the filter. Whether NAMEs are stable is unverified (same exposure as upstream).
5. **Intent-first fallback replaces the intent** (lifecycle). On a rebuild where the intent's row is missing but the saved preference resolves to a different row, `applySubtitleSelection(persist = false)` (`:869-881`) overwrites the intent, so the original row is no longer re-applied if it returns. The two differ only if the save failed, which D4 removes, so this is rare. It lives in D9.
6. **Old preferences change only from Off to a pick** (rollout). The manifest-preferring language rule (D6 rule 5) resolves cases that the old unambiguous rule left Off. Every previously resolved case is unchanged. See Q1 (AC-4 wording).
7. **Downgrade or upstream data** (rollout). An older fork build reading a new `subtitle_url_ = subtitle:<NAME>` finds no URL or stable-key match (`:98-112`) and falls back to language, which is harmless. Downgrades are not authorised on devices anyway. Data written by an upstream build stored the prefixed group id. Equality still matches it within the same merge shape, and otherwise it falls back to language.
8. **Sequencing** (merge conflicts). `player-vm-split` extracts coordinators from the same `PlayerVM` functions (`:599-616`, `:1849-1870`). `subtitle-switch-text-disable` (Part 2) relies on D9's re-send after `switchStream`, which this run does not change.
9. **Performance, concurrency, focus, Koin, recomposition.** Nothing material. Restore and save run on the main thread as today, with two more in-memory `SharedPreferences` reads. There is no new state in Compose, no new scope, no new coroutine and no D-pad path. Baseline profile: unaffected.

### Open questions

1. **AC-4 says "resolves exactly as before"**, but D6 rule 5 (from upstream, brief scope 3: "language match that prefers manifest rows") can turn an old preference that was ambiguous, and so Off, into the single manifest row. Recommended: reword AC-4 to "every saved preference from before this change that resolved to a row resolves to the same row; the existing resolver tests pass unchanged; a formerly ambiguous language may now resolve to the single manifest row". Blocks: yes (AC wording, gate 1).
2. **Should a restore that found nothing keep the saved preference until the viewer picks?** (Risk 3.) This was not asked for, and the gap exists today. Recommended: yes. In `PlayerVM.kt`, set a `subtitlePickedThisMedia` flag in `applySubtitleSelection` when `persist` is true, and reset it in `resetMediaSession` (`:978-997`). `saveTrackPreferences` then writes the intent only when `intendedSubtitleRow != null || subtitlePickedThisMedia`, and otherwise passes the stored preference through (upstream's `preserveStoredPreference`). This adds one AC, for example "Given the saved subtitle did not resolve and the viewer made no pick, When audio is changed, Then the saved subtitle is kept". Blocks: yes (it adds an AC; without it AC-5's benefit is lost on the first audio change).
3. Should `findSameSubtitleTrackIndex` also gain the readable-label rule (brief: "optionally")? Recommended: no, since no AC needs it (D11). Blocks: no.

## Test design

<!-- owner: test-designer (test-design.md) -->

Base `f351d988`. Scope: AC-1..AC-12 (AC-13..15 live in `subtitle-switch-text-disable`). Inputs: `brief.md`,
`design-architect.md` (D1-D12, R1-R9). Test paths relative to `app/src/test/kotlin/com/kino/puber/`.
Levels: `unit` (JUnit 5, `MainDispatcherExtension`, mockk fakes as in `PlayerVMTestFixture`, `FakeSharedPreferences`),
`device` (emulator, D-pad, evidence file). No `instrumented` tests in this run.

### Pending gate-1 answers (the design assumes the architect's recommendations)

| Q | Assumed answer | Test consequence | If the owner answers otherwise |
|---|---|---|---|
| Q1 | AC-4 reworded: an old preference that resolved to a row resolves to the same row; a formerly ambiguous language may now resolve to the single manifest row | AC-4 tests pin only "resolved before => same row" plus the existing `AudioTrackPreferenceResolverTest.kt:115-126` unchanged. One test pins the new Off-to-manifest-row case (T-4c) | Original wording ("exactly as before"): T-4c flips to expect `NO_MATCH` and D6 rule 5 loses its manifest preference; the architect must change the design. T-4c is the only test affected |
| Q2 | New AC-16: saved preference kept after a restore that found nothing, until the viewer picks (`subtitlePickedThisMedia`) | Tests T-16a..c below (VM level) | Not adopted: drop T-16a..c; add a known-gap note: after AC-5's forced-only case the first audio change wipes the saved full subtitle. T-8c and the pass-through tests stay |
| Q3 | No readable-label rule in `findSameSubtitleTrackIndex` | No test; existing `findSameSubtitleTrackIndex_*` tests stay unchanged | Adopted label rule: add one resolver test for two same-language labelled rows in the in-session re-find |

AC-16 text used here: Given the saved subtitle did not resolve on this stream and the viewer made no subtitle pick, When
the audio track is changed (or any other save runs), Then the stored subtitle preference (all four values) is written back
unchanged; and Given the viewer then picks a subtitle or Off, Then the new value replaces it.

### 1. Risk profile

Score = probability (1-3) x impact (1-3). Score >= 6 has a test at the level where it fails.

| ID (design) | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Stale or partial keys: a forced flag or label survives Off and filters rows later | 2 | 3 | 6 | T-3a, T-3b (repository, real `FakeSharedPreferences`) |
| R2 | Identity collision / wrong row from a stored `subtitle:<NAME>`; side-load positional id `<n>:0` stored | 2 | 3 | 6 | T-1d, T-10a, T-10b, T-1e, T-2c (unit, resolver and key) |
| R3 | Saved preference wiped after a restore that found nothing (data loss, pre-existing) | 3 | 3 | 9 | T-16a..c (VM). Without Q2 it is accepted as a known gap |
| G1/G2 | Manifest-only row saved as Off, or language only (the bug itself) | 3 | 3 | 9 | T-1a, T-2a (VM save), T-1b (repository), device AC-11 |
| G3 | Forced-only row chosen for a full-subtitle viewer | 2 | 2 | 4 | T-5a, T-5b (accepted at unit level, below 6) |
| R4 | Over-strict label filter after a NAME rename gives Off | 2 | 2 | 4 | T-6d (tolerance: row without readable label passes). Rename itself is `unverified`, accepted with a note |
| R5 | Intent-first fallback overwrites the intent | 1 | 2 | 2 | T-8c pins the order only. Accepted |
| R6 | Old preferences change outcome | 2 | 3 | 6 | T-4a, T-4b, existing resolver tests, T-4c |
| R7 | Downgrade or upstream-written data | 1 | 2 | 2 | T-1d covers the prefixed-equality case. Accepted |
| R8 | Sequencing with `player-vm-split` and Part 2 (D9 re-send after `switchStream`) | 2 | 2 | 4 | No test; process risk. T-8a pins the D9 re-send that Part 2 depends on |
| R9 | Performance/concurrency/focus | 1 | 1 | 1 | Accepted, nothing material |
| New | Migration breakage of 47 mockk verifies (D3): a silently weaker `verify` | 2 | 3 | 6 | Compile-breaking signature (no defaults). Reviewer rule: every `saveTrackPreferences` verify must use the `SubtitlePreference` matcher. T-8b, T-9 keep `exactly = 0` negatives with `any()` on the new arity |
| New | Rebuild intent-first branch re-sends but also persists, or double-sends | 2 | 2 | 4 | `rebuildFastDns_*` / `rebuildBufferPreset_*` stay green (`exactly = 1` `selectSubtitle`, zero saves) |

### 2. Traceability

Priority: P0 must exist before review, P1 should, P2 nice to have.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 | T-1a `PlayerVMTrackSelectionTest.pickManifestOnlyRow_savesUnprefixedGroupIdWithLanguageForcedAndLabel` | Picking a manifest-only row with language (group id `0:subtitle:Rus`) and then an audio pick stores `SubtitlePreference("rus", "subtitle:Rus", forced, label)`; no `0:` prefix |
| AC-1 | unit | P0 | T-1b `PlayerPreferencesRepositoryTest.subtitlePreference_roundTripsAllFourValues` | `saveTrackPreferences` then `getPreferredSubtitlePreference` through a new repository instance returns the same four values |
| AC-1 | unit | P0 | T-1c `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_matchesManifestRowByStoredGroupId` | New session with stored `subtitle:Rus` picks the manifest row |
| AC-1 | unit | P1 | T-1d `findSubtitleTrackIndex_matchesPrefixedGroupIdAgainstStoredUnprefixedAndViceVersa` | A stored `subtitle:Rus` matches `0:subtitle:Rus` row and a stored prefixed upstream value still matches by equality (R2, R7) |
| AC-1 | unit | P0 | T-1e `PlayerVMTrackSelectionTest.newSession_restoresManifestOnlyRowFromSavedPreference` | End to end through the VM: saved preference in the interactor stub, tracks reported, the intended row is `selectSubtitle`d with `persist=false` (zero saves) |
| AC-2 | unit | P0 | T-2a `PlayerVMTrackSelectionTest.pickLanguageLessManifestRow_savesIdentityNotOff` | Language-less manifest pick saves `language=null, url="subtitle:<NAME>"`, not `NONE` (fails on base: G1) |
| AC-2 | unit | P0 | T-2b `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_preservesLanguageLessTrackByIdentity` | Ported from f9c9ff38; the language-less row is chosen by identity among several rows |
| AC-2 | unit | P1 | T-2c `SubtitlePreferenceKeyTest.languageLessRow_givesNullLanguageNeverEmpty` | `toSubtitlePreference()` language is null, not `""` (guards D6 rule 0) |
| AC-3 | unit | P0 | T-3a `PlayerPreferencesRepositoryTest.subtitlePreference_noneClearsAllFourKeys` | After saving a full preference, saving `NONE` leaves `subtitle_lang_/url_/forced_/label_` missing; reader returns `NONE` with `isForced == null` (not `false`) |
| AC-3 | unit | P0 | T-3b `PlayerVMTrackSelectionTest.explicitOff_savesNoneAndClearsAllValues` | After picking a row, then picking Off, the next save is exactly `SubtitlePreference.NONE` |
| AC-3 | unit | P1 | T-3c `PlayerPreferencesRepositoryTest.subtitlePreference_partialValuesRemoveOnlyNullKeys` | Saving `(lang, null, null, null)` after a full value removes the three others (null removes the key) |
| AC-4 | unit | P0 | existing `findSubtitleTrackIndex_matchesRuAgainstRus_viaSameSubtitleLanguage` and `_stillAmbiguous_returnsNoMatch` (`:115-126`), unchanged | Old resolver outcomes via URL, language alias and ambiguity stay |
| AC-4 | unit | P0 | T-4a `PlayerPreferencesRepositoryTest.legacyKeysOnly_readAsLangUrlWithNullForcedAndLabel` | Seeding only `subtitle_lang_42`/`subtitle_url_42` reads `(lang, url, null, null)` |
| AC-4 | unit | P0 | T-4b `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_legacyPreference_resolvesToSameRowAsBefore` | Table over old cases: exact URL, stable key (different host or token), single-language row, with forced/label null; the index equals the base result. Includes a URL match on a row that the semantic filter would drop (rules 1-2 are unfiltered) |
| AC-4 | unit | P1 | T-4c `findSubtitleTrackIndex_legacyAmbiguousLanguage_nowResolvesToSingleManifestRow` | Q1 wording: two same-language rows, exactly one `fromManifest`, language-only preference gives the manifest row (was NO_MATCH). Two manifest rows stay NO_MATCH |
| AC-5 | unit | P0 | T-5a `AudioTrackPreferenceResolverTest.findSubtitleTrackIndex_rejectsForcedOnlyReplacementForFullPreference` | Ported from 7d5addd2: preference `(rus, null, forced=false)`, only a forced rus row gives NO_MATCH, not the forced row |
| AC-5 | unit | P0 | T-5b `PlayerVMTrackSelectionTest.savedFullSubtitle_streamHasOnlyForcedRow_staysOff` | VM: no `selectSubtitle` for the forced row, selected index is Off. Fails on base (G3: legacy language rule picks it). Note: the legacy lang-only preference (forced null) still picks the forced row; that is by design (AC-4) |
| AC-6 | unit | P0 | T-6a `findSubtitleTrackIndex_usesForcedFlagToChooseBetweenFullAndForcedSameLanguage` | Two rus rows, preference forced=false gives the full row, forced=true gives the forced row |
| AC-6 | unit | P0 | T-6b `findSubtitleTrackIndex_usesMeaningfulLabelToResolveSameLanguageVariants` | Ported from 7d5addd2: two labelled rus manifest variants; stored label chooses the right one |
| AC-6 | unit | P1 | T-6c `findSubtitleTrackIndex_labelComparisonIsTrimmedAndCaseInsensitive` | D6 rule 4 normalisation |
| AC-6 | unit | P1 | T-6d `findSubtitleTrackIndex_rowWithoutReadableLabelPassesLabelFilter` | D7 tolerance: preference with label, API/side-load row without a readable label is still matched by URL or language (progressive fallback, R4) |
| AC-6 | unit | P2 | T-6e `SubtitlePreferenceKeyTest.readableLabelOnly_fileKeyLabelIsNotStored` | A side-load whose label is the file key stores `descriptiveLabel = null` |
| AC-7 | unit | P0 | T-7a `findSubtitleTrackIndex_doesNotRestoreOffWhenLanguageLessIdentityDisappears` | Ported from f9c9ff38: language null, identity absent gives NO_MATCH, not the Off index |
| AC-7 | unit | P0 | T-7b `findSubtitleTrackIndex_emptyLanguageWithoutIdentity_meansOff` | Empty language, null/empty url gives the Off index; this is rule 0 |
| AC-8 | unit | P0 | T-8a `PlayerVMTrackSelectionTest.rebuild_intentDiffersFromSaved_resolvesIntentFirst` | Saved says eng, live intent is rus; after a Fast DNS rebuild and report, `selectSubtitle(rus)` once, no eng, zero saves |
| AC-8 | unit | P0 | existing `PlayerVMTrackSelectionTest.kt:401-457` (`rebuildFastDns_*`, `rebuildBufferPreset_*`), migrated to the new signatures only | Intent order and no persist stay |
| AC-8 | unit | P1 | T-8c `rebuild_intentRowMissing_fallsBackToSavedPreference` | Intent row absent from the report, saved resolves: restored with `persist=false`; pins R5 behaviour |
| AC-8 | unit | P1 | T-8b `newMedia_intentIsNull_usesSavedPreferenceOnly` | After `resetMediaSession` the saved preference alone is used |
| AC-9 | unit | P0 | T-9 `rebuildFastDns_audioEmptyReportWithTextRows_reSendsIntent_andSavesNothing` | Rebuild, a report with empty audio and text rows: one `selectSubtitle(pick)`, zero saves, audio rows kept. No production change expected, so it passes on base (regression pin) |
| AC-10 | unit | P0 | T-10a `SubtitlePreferenceKeyTest.sideLoadRow_neverStoresPositionalGroupId` | Side-load with group id `0:0`/`1:0`: key is `url`, else `sourceFile`, else stable `playerTrackId`, never `<n>:0` |
| AC-10 | unit | P0 | T-10b `PlayerVMTrackSelectionTest.pickSideLoadedRow_savesUrlNotGroupId` | Through the VM; saved url equals the API URL, no `<n>:0` anywhere in the saved preference |
| AC-10 | unit | P1 | T-10c `findSubtitleTrackIndex_positionalSideLoadGroupIdNeverMatches` | A stored `0:0` does not match a side-load row that happens to have that group id (identities exclude it for non-manifest rows) |
| AC-10 | unit | P1 | `SubtitlePreferenceKeyTest.keyOrder_urlThenSourceFileThenGroupIdThenTrackUriThenTrackId` | D4 order, and all-empty gives null never `""` |
| AC-11 | device | P0 | Manual checks M1-M4 | Persistence, playback and re-entry on a real HLS title |
| AC-12 | unit | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | Whole gate green. The 47 migrated `saveTrackPreferences` verifies compile and keep their negative cases |
| AC-16 (Q2) | unit | P0 | T-16a `PlayerVMTrackSelectionTest.savedSubtitleUnresolved_noPick_audioChangeKeepsStoredPreference` | Stored preference unmatched (forced-only stream or missing row), intent null, `SelectAudioTrack`: the save carries the stored `SubtitlePreference` unchanged, not `NONE`. Fails on base (the base writes null/null at `PlayerVM.kt:1856-1858`) |
| AC-16 (Q2) | unit | P0 | T-16b `..._pickAfterUnresolved_replacesStored` | After the viewer picks a row, the save carries the new preference |
| AC-16 (Q2) | unit | P1 | T-16c `..._explicitOffAfterUnresolved_clearsStored` | Explicit Off sets the flag, so `NONE` is written; also the flag resets on new media (`resetMediaSession`) so the next title's unresolved restore passes through again |

Failing-first selection among the traceability rows: see section 3.

### 3. Bugs (tests that fail on the base commit today)

Base read with `git -C <wt> show f351d988:<path>`; not checked out.

- G1 (language-less manifest row saved as Off): `PlayerVM.kt:1849-1871` writes `language`/`url` only when non-empty, so a manifest row with `url=""` and no language writes null/null, which removes both keys. T-2a fails today.
- G2 (manifest row with language stores only the language): T-1a fails today.
- G3 (forced-only row chosen for a full viewer): `AudioTrackPreferenceResolver.kt` rule `unambiguousSubtitleLanguageMatch` ignores forced, and `PlayerVM.kt:606-608` applies it with `persist=false`. T-5b fails today.
- R3 (wipe after an unresolved restore): T-16a fails today.

**Failing-first test: T-2a `PlayerVMTrackSelectionTest.pickLanguageLessManifestRow_savesIdentityNotOff`**, written first.
Compile caveat: the tests use the new `SubtitlePreference` signature, which does not exist on base. To show a red run on the base
commit, first write T-2a (and T-5b) as a throwaway against the base signature
(`saveTrackPreferences(42, any(), any(), null, null)` is called, so `verify(exactly=0)` of that null/null call fails) and run it
once red. After the signature migration, the same test is rewritten against `SubtitlePreference` and must be red against a stub
that returns `NONE`, then green. Order for the implementer: T-2a and T-5b red on base, then the model, then green.

Tests that pass on base and are regression pins: T-9, existing `:401-457`, T-4a/b (as a table of old outcomes), T-7b.

### 4. Test-fixture notes

- `PlayerVMTestFixture.kt:107-108,116`: replace the two `getPreferredSubtitleLang/Url` stubs with
  `every { interactor.getPreferredSubtitlePreference(any()) } returns SubtitlePreference.NONE`, and the save stub with
  a 4-argument `any()`. Add a helper `stubSavedSubtitle(SubtitlePreference)` to avoid per-test mockk wiring (the same helper name
  upstream calls `stubPreferredSubtitle`).
- Add a `manifestRow(lang, groupId, forced, label)` builder in the fixture or beside `AudioTrackPreferenceResolverTest.kt:13-19`
  (upstream `manifestTrack(...)` in `PlayerVMSubtitleVariantTest` is the model).
- Port sources (read with `git show <sha>:app/src/test/...`, port by intent, keep fork names):
  - 7d5addd2 `AudioTrackPreferenceResolverTest`: `rejectsForcedOnlyReplacementForFullPreference`, `usesMeaningfulLabelToResolveSameLanguageVariants`,
    `doesNotUseCoordinatesAcrossMediaSources` (it asserts the absence of a coordinate rule, which holds here: no coordinates), `rejectsReusedIdentityWithDifferentLanguage`
    (check against the fork's D7 language clause: row language blank is tolerated, a different language is not).
  - 7d5addd2 `PlayerPreferencesRepositoryTest.subtitlePreferenceMetadata_persistsAndClearsSemanticIdentity` becomes T-1b plus T-3a.
  - f9c9ff38 `preservesLanguageLessTrackByIdentity`, `doesNotRestoreOffWhenLanguageLessIdentityDisappears` become T-2b, T-7a.
  - `PlayerVMSubtitleRestoreTest` (7d5addd2): port its rebuild/pass-through cases only where Part 1 owns the behaviour (T-8a, T-9, T-16). Its `qualityChange_*` and `switchStream_*` cases belong to `subtitle-switch-text-disable` (they assume the text disable in `switchStream`) and are not ported here.
  - Do not port: `PlaybackControllerSubtitleDefaultsTest`, `PlaybackControllerNetworkTracksTest` (instrumented), `PlayerScreenE2ETest`.
- Tests assert through the public surface: the resolver's return value, the repository reader, `interactor.saveTrackPreferences` arguments, `playbackController.selectSubtitle` calls, and VM content state. The class under test (`PlayerVM`, `PlayerPreferencesRepository`) is never mocked; the interactor, controller and mapper are the fakes, as in the existing fixture.
- `SubtitlePreferenceKey.kt` helpers are `internal`, called directly by `SubtitlePreferenceKeyTest`; they are pure functions.

### 5. Manual checks (AC-11, device)

Owner decision: HLS title with a manifest-only row (Psyren S1E1 or Outlander). Lease an emulator via
`tools/emulator-resource-lock.sh`, `adb -s <serial>` for every adb call, install the worktree's own dev APK with
`tools/android-apk-install-preserve`. No uninstall, no data clear. Evidence goes to the run's evidence file, with
`tools/mobile-evidence-audit.sh` run before sharing. Forced and language-less cases are unit-only (no title is known).

1. Open the title, start playback on HLS (the debug overlay or stream info shows `.m3u8`). Open the subtitle picker; the list shows the manifest-only row (no API URL; marked by the debug label or log). Select it.
   Expected: the subtitle renders cues. Must not: the picker shows Off after the pick, or the pick selects another language.
2. Press Back, leave the player, and open the same episode again.
   Expected: the same row is already selected in the picker and cues render without any pick (M1, core of AC-11). Must not: Off, a different row, or the API variant of the same language.
3. Within the same session, change quality (settings, a different HLS quality), wait for tracks.
   Expected: the same subtitle row is still on after the switch (observation only; no production change in this run). Must not: Off, or a different row. A flash of cues going off and on is not a defect here. If Off or wrong after the switch, record it as a finding for `subtitle-switch-text-disable`, not as a failure of this run.
4. After the quality switch, exit and re-enter once more.
   Expected: the same row again (the save was not damaged by the switch). Must not: Off.
5. (Rollout) With a stale old-style preference on the device from before the install (the install preserves data), open a title where the viewer previously picked a subtitle.
   Expected: the previous pick still resolves (AC-4), or a title that used to be Off because of ambiguity may now show the single manifest row (Q1). Must not: a crash, or a forced-only row for a full viewer.
6. Pick Off explicitly, leave, re-enter.
   Expected: Off stays. Must not: a subtitle returns.

Logs: only the player's own subtitle-restore lines, filtered by tag; no broad logcat in evidence.

### 6. UNTESTABLE

- AC-11 on forced and language-less rows by device: no title with a forced or language-less manifest row is known (sideload-merge AC-2/5/9 NOT CHECKED). Covered by unit tests T-2a, T-2b, T-5a, T-5b only; the unit level is the accepted evidence by the owner's decision.
- R4 NAME stability across sessions on the CDN: `unverified`, cannot be asserted offline. Device observation M1 over two sessions is the only (weak) evidence.
- The Media3 1.11.0 group id shapes (`subtitle:<NAME>` and `0:subtitle:<NAME>`) are asserted in unit tests from `research.md: 5` fixtures, not from a live ExoPlayer; a live check needs the instrumented fixtures of P6-A (Part 2). M1 on the device covers the real id on one title.
- Downgrade behaviour (R7): device downgrades are not authorised; only the equality case is unit-tested.

Perf track: off (tracks: none). No metric, baseline, or threshold.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | AC-4 wording vs the manifest-preferring language rule | yes | answered | Reword (recommended) |
| Q2 | Keep the saved preference after a restore that found nothing (new AC-16) | yes | answered | Yes (recommended) |
| Q3 | Readable-label rule in findSameSubtitleTrackIndex | no | answered | No (recommended) |
| Q4 | AC-11 after a quality switch when manual quality plays url.hls | yes | answered | Reword: on when the stream offers the row, else Off without saving, back on Auto / re-entry (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-14 | Rebuild restore: intent first, then saved (AC-8) | owner |
| 2026-10-14 | Device scope: exit/re-enter on an HLS title with a manifest-only row, then after a quality switch; forced and language-less unit-only | owner |
| 2026-10-15 | Split: this run is Part 1 (AC-1..AC-12); option C to run subtitle-switch-text-disable after P6-A | owner |
| 2026-10-15 | Q1-Q4 answered as recommended; AC-4 and AC-11 reworded, AC-16 added | owner (standing instruction: recommended) |
| 2026-10-15 | Gate 1: spec approved | owner (standing instruction: recommended) |
