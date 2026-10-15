# Design (architect): subtitle-restore-model, Part 1

Base `f351d988`, worktree `/home/user1/orca/workspaces/Puber/subtitle-restore-model`. Scope: option B, AC-1..AC-12
(AC-12 = `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`, no instrumentation compile).
This builds on the sideload-merge model (spec `docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md`
D11 amended, D17-D20). The VM owns `intendedSubtitleRow`, the restore waits for text, and the controller's
pending row is only a mirror. That model is unchanged. Upstream 7d5addd2 / f9c9ff38 is ported by intent, not
line by line. App paths below are relative to `app/src/main/java/com/kino/puber/`, test paths to
`app/src/test/kotlin/com/kino/puber/`. Every `path:line` was read at `f351d988`.

## Decisions

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

## Changes by layer

### API / repository

- New `domain/model/SubtitlePreference.kt` (D1).
- `data/repository/PlayerPreferencesRepository.kt`:
  - `:16-22`: replace the two public getters with `getPreferredSubtitlePreference(itemId)`. It reads lang and url with `getString`, forced with `contains` plus `getBoolean`, and label with `getString` (D2).
  - `:28-56` `saveTrackPreferences(itemId, audioLang, audioLabel, subtitle: SubtitlePreference)`: the four subtitle keys are put or removed in the same `edit {}` (D2, D3).
  - `:121-122`: add `KEY_SUBTITLE_FORCED_PREFIX = "subtitle_forced_"` and `KEY_SUBTITLE_LABEL_PREFIX = "subtitle_label_"`.
- `domain/interactor/player/PlayerInteractor.kt:335-358`: one getter and the new save signature, both delegating (D3).
- API models and `KinoPubApiClient`: unchanged.

### ViewModel state and actions

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

### Screen and DI module

- None (D12).

### Compose content

- None.

### Resources

- None.

### Tests (inputs for the test-designer; existing files)

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

## Alternatives rejected

- A new key `subtitle_track_<id>` for the manifest identity: the URL slot already holds identities and upstream's tests assume it, and an extra key would be one more thing to clear on Off.
- Persisting the full, prefixed group id: it changes whenever the API subtitle list appears or disappears (`0:` prefix), so restores would silently fail.
- Upstream's strict forced and label filter: it turns URL-saved preferences into Off on progressive or fallback streams in the fork (D7).
- Running the identity rules only over filtered rows, including URL equality: this changes old-preference outcomes, which AC-4 forbids.
- Adding a fourth and fifth default parameter to `saveTrackPreferences` (upstream): with mockk this silently weakens 47 verifies (D3).
- A schema version or migration pass: old entries are already a valid subset (D2).
- Resolving the intent through `findSubtitleTrackIndex` (upstream `previousSubtitle ?: saved` field by field): `findSameSubtitleTrackIndex` already re-finds it with more information (group id plus index, player track id), so re-resolving would be weaker and duplicated.
- A label rule in `findSameSubtitleTrackIndex`: no AC needs it (D11).
- Changing `prepare()` to keep `pendingSubtitleTrack`: out of scope, and not needed (D10).

## Risks (highest first)

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

## Open questions

1. **AC-4 says "resolves exactly as before"**, but D6 rule 5 (from upstream, brief scope 3: "language match that prefers manifest rows") can turn an old preference that was ambiguous, and so Off, into the single manifest row. Recommended: reword AC-4 to "every saved preference from before this change that resolved to a row resolves to the same row; the existing resolver tests pass unchanged; a formerly ambiguous language may now resolve to the single manifest row". Blocks: yes (AC wording, gate 1).
2. **Should a restore that found nothing keep the saved preference until the viewer picks?** (Risk 3.) This was not asked for, and the gap exists today. Recommended: yes. In `PlayerVM.kt`, set a `subtitlePickedThisMedia` flag in `applySubtitleSelection` when `persist` is true, and reset it in `resetMediaSession` (`:978-997`). `saveTrackPreferences` then writes the intent only when `intendedSubtitleRow != null || subtitlePickedThisMedia`, and otherwise passes the stored preference through (upstream's `preserveStoredPreference`). This adds one AC, for example "Given the saved subtitle did not resolve and the viewer made no pick, When audio is changed, Then the saved subtitle is kept". Blocks: yes (it adds an AC; without it AC-5's benefit is lost on the first audio change).
3. Should `findSameSubtitleTrackIndex` also gain the readable-label rule (brief: "optionally")? Recommended: no, since no AC needs it (D11). Blocks: no.
