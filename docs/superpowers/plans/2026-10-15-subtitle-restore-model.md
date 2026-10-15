# Persist and restore the chosen subtitle across player recreation - plan

**Spec:** `docs/superpowers/specs/2026-10-15-subtitle-restore-model.md`
**Goal:** The chosen subtitle is saved as a four-value `SubtitlePreference`: language, an identity key that also covers manifest-only rows, the forced flag and a readable label. A restore in a new session resolves it semantically. A rebuild restores the live intent before the saved value. A restore that finds nothing no longer wipes what is stored. This is Part 1, AC-1..AC-12 and AC-16.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Worktree: `/home/user1/orca/workspaces/Puber/subtitle-restore-model` (branch
  `subtitle-restore-model`, code base `f351d988`; the spec commits `9b1dba8a` and `866afbd0` touch no code).
  Run every command there (`cd <wt> && ...`). Never run `./gradlew` in a worktree, because the guard hook
  refuses it. Always prefix Gradle with `AGENT_GRADLE_SERIAL=1`.
- Fast gate (`check.fast`): `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`.
  Full gate (`check.full`, AC-12): `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests:
  `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<FQCN>'`. The fast gate compiles
  production sources only, so it does not check that test sources compile. After every task, run the touched
  test classes as well. Before each commit, also run the whole player package and the repository test:
  `--tests 'com.kino.puber.ui.feature.player.*' --tests 'com.kino.puber.data.repository.PlayerPreferencesRepositoryTest'`.
- One task means a green fast gate, green touched tests and one commit. The commit message is one
  imperative English sentence with no prefix. **Never add a `Co-Authored-By` line**, whatever the harness
  suggests. Each task gives a suggested message. Stage explicit paths only (`git add <path>...`), never
  `git add -A` or `git add .`. The conductor commits this plan file and the spec; the implementer does not.
- Test-first. The tests listed under "Write first" are written **before** the production change of their
  task. Run them and see them fail, then record the failing test names and the assertion line in the
  Implementation record. The spec's failing-first tests are T-2a, T-5b and T-16a (Test design section 3).
  Each is shown red twice:
  1. On the base commit, written against the **old** signature (Task 1, step 1).
  2. Against the new signature, before the VM change (Task 4, "Write first").
- Unit tests are JUnit 5 (`org.junit.jupiter.api.*`). VM tests extend `PlayerVMTestFixture` and register
  `MainDispatcherExtension` as in `T/vm/PlayerVMTrackSelectionTest.kt:35-39`. They do not use `runTest`
  (`:28-31`). No unit test may build `MediaItem`, `Uri` or `ExoPlayer`.
- Mockk rule (spec D3): the interactor's `saveTrackPreferences` gets **no default parameters**. Every
  `verify` of it names the fourth argument explicitly, in one of three forms:
  - `any()` for "any save";
  - `SubtitlePreference.NONE` for Off;
  - `match { ... }` / an `eq` value for a concrete preference.

  The fourth argument is never left out.
- Detekt runs over production and unit-test sources (`app/build.gradle.kts:340`). The limits that bite here
  (`config/detekt/detekt.yml`) are:
  - `ReturnCount` max 3 (`:756-764`; guard clauses count). Use matcher lists, as
    `M/vm/AudioTrackPreferenceResolver.kt:31-38` does.
  - `ComplexCondition` 3 (`:81-83`).
  - `CyclomaticComplexMethod` 14 (`:90-92`).
  - `LongMethod` 60 (`:113-116`).
  - `UnusedParameter` (`:817-820`).

  Do not add suppressions or baseline entries; restructure instead.
- Scope (spec "Out of scope"): no change to `PlaybackController` (including `prepare()` and `switchStream`),
  `SubtitleTrackMerger`, `SubtitleTrackSelector`, `findSameSubtitleTrackIndex`, DI, screens, Compose, strings,
  or audio preference persistence. There is no instrumented test.
- Device verification for AC-11 (spec Test design section 5, M1-M6, with the reworded M3 for the quality switch)
  belongs to qa-device after Task 4. No task
  here installs, launches or touches a device.
- Upstream sources are read from the **main checkout**, ported by intent and keeping the fork's names:
  `git -C /mnt/data/projects/PetProjets/Puber show <sha>:<path>`.
  - `7d5addd2:app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt` (`preferenceKey()`,
    `preserveStoredPreference` in `saveTrackPreferences`)
  - `f9c9ff38:app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt` (rule
    shape: `isSemanticallyCompatibleWith`, `subtitleDescriptiveLabelMatch`, `subtitleLanguageMatch`,
    `normalizedPreferenceLabel`)
  - `7d5addd2:app/src/test/kotlin/com/kino/puber/data/repository/PlayerPreferencesRepositoryTest.kt`
    (`subtitlePreferenceMetadata_persistsAndClearsSemanticIdentity`)
  - `7d5addd2:` and `f9c9ff38:app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt`
  - `7d5addd2:app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt` (`stubPreferredSubtitle`)
  - `7d5addd2:app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMSubtitleRestoreTest.kt` (model only; its
    `qualityChange_*`/`switchStream_*` cases belong to `subtitle-switch-text-disable` and are **not** ported)
- Path abbreviations (worktree):
  - `M/` = `app/src/main/java/com/kino/puber/`
  - `T/` = `app/src/test/kotlin/com/kino/puber/`
  - `VM` = `M/ui/feature/player/vm/PlayerVM.kt`
  - `RES` = `M/ui/feature/player/vm/AudioTrackPreferenceResolver.kt`
  - `TST` = `T/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`
  - `FIX` = `T/ui/feature/player/vm/PlayerVMTestFixture.kt`

  Every `path:line` was read at `f351d988`. Later tasks say when an earlier task has moved lines; when in
  doubt, find the symbol by name.

### Plan refinements (no AC changes; reviewers check these)

The owner gave a standing instruction: no questions, and the recommended option wherever something is undecided.
Each choice below is that option.

- **P-a (empty URL guard).** Rules 1 and 2 return no match when `preferredUrl` is null **or empty**. Today
  `exactSubtitleUrlMatch` checks only null (`RES:102`). An empty stored URL would match the Off row (url `""`),
  and `"".stableSubtitleKey()` is `""`, which matches every row without a URL. No build ever stored `""`: the
  VM writes `takeIf { it.isNotEmpty() }` (`VM:1857-1858`). So AC-4 is unaffected.
- **P-b (Off rows out of the candidates).** D7 says Off rows are "always compatible", but no rule except
  rule 0 may pick them. The plan therefore leaves them out of the candidate list altogether. The result is
  the same, and the code is simpler.
- **P-c (shared test rows).** One `internal` builder file, `T/ui/feature/player/vm/SubtitleTestRows.kt`,
  holds `manifestRendition(...)`. The resolver, key and VM tests share it, so there are not three private
  copies.
- **P-d (migration size).** The design counts "47" calls. At `f351d988` the real numbers are:
  - `TST` has 17 `saveTrackPreferences` verifies (lines 98, 158, 174, 208, 375, 391, 414, 415, 431, 456, 467,
    482, 504, 519, 649, 663, 678).
  - `TST` has 15 lang/url stub pairs (123, 136, 150, 164, 182, 199, 395, 490, 511, 602, 617, 629, 642, 655, 670).
  - `FIX:107-108,116` has 3 fixture stubs.

  All of them are migrated in Task 1.
- **P-e (flag name).** The AC-16 flag is `subtitlePickedThisMedia`, the name the spec's Q2 and Test design use.
- **P-f (not ported).** Upstream `findSubtitleTrackIndex_doesNotUseCoordinatesAcrossMediaSources` is not
  ported, because the fork's signature has no coordinate parameters and so nothing exists to assert. The
  absence of coordinates is structural.
- **P-g (Task 1 is behaviour-neutral).** Task 1 changes signatures only. The VM keeps today's save values
  and today's two-field restore, so the failing-first tests stay red until Task 4.
- **P-h (T-5b on base).** The base signature cannot carry `forced = false`. T-5b's base form uses the legacy
  preference `("rus", null)`, which on base picks the only (forced) row; that is G3 and makes it red. The final
  form adds `isForced = false`. The legacy form keeps picking the forced row after this run, by design (AC-4),
  so the final T-5b must use the new form.

---

## Tasks

### Task 1: Introduce `SubtitlePreference` with forced and label keys, and migrate every caller and test

**Covers:** AC-1 (repository half), AC-3 (repository), AC-4 (repository), groundwork for all others
**Files:**
- Create: `M/domain/model/SubtitlePreference.kt`
- Modify: `M/data/repository/PlayerPreferencesRepository.kt` - replace the getters `getPreferredSubtitleLang` (`:16-18`) and
  `getPreferredSubtitleUrl` (`:20-22`) with `getPreferredSubtitlePreference`. Change `saveTrackPreferences` (`:28-57`)
  to take a `SubtitlePreference`. Add the keys next to `KEY_SUBTITLE_LANG_PREFIX`/`KEY_SUBTITLE_URL_PREFIX` (`:121-122`).
- Modify: `M/domain/interactor/player/PlayerInteractor.kt` - replace `getPreferredSubtitleLang` (`:335-337`) and
  `getPreferredSubtitleUrl` (`:339-341`) with one getter, and change `saveTrackPreferences` (`:343-357`).
- Modify: `VM` - the call sites only: `restoreSubtitlePreference` (`:599-616`, reads at `:600-601`, resolver call
  `:603-607`) and `saveTrackPreferences` (`:1849-1870`).
- Modify: `FIX` - the stubs at `:107-108` and `:116`, plus a new helper.
- Modify: `TST` - the 15 stub pairs and 17 verifies listed in P-d.
- Test: `T/data/repository/PlayerPreferencesRepositoryTest.kt` (fixture `:33-44`, built on
  `T/util/FakeSharedPreferences.kt`. That fake supports `getString`, `getBoolean`, `contains`, `putString`,
  `putBoolean` and `remove` (`:25-51`), and `apply()` commits puts and removals together (`:52-59`). Its map is `values` (`:16`).)

**Consumes:** nothing new.
**Produces:**
```kotlin
// M/domain/model/SubtitlePreference.kt
package com.kino.puber.domain.model
data class SubtitlePreference(
    val language: String?,
    val url: String?,
    val isForced: Boolean?,
    val descriptiveLabel: String?,
) { companion object { val NONE = SubtitlePreference(null, null, null, null) } }

// PlayerPreferencesRepository and PlayerInteractor (no default parameters)
fun getPreferredSubtitlePreference(itemId: Int): SubtitlePreference
fun saveTrackPreferences(itemId: Int, audioLang: String?, audioLabel: String?, subtitle: SubtitlePreference)

// FIX
protected fun stubSavedSubtitle(
    language: String?, url: String?, isForced: Boolean? = null, descriptiveLabel: String? = null,
)   // every { interactor.getPreferredSubtitlePreference(42) } returns SubtitlePreference(...)
```

Steps:

- [x] **Red on base (no commit, before any other edit).** In `TST`, add the three failing-first tests against
  the **old** signature. Use the existing private helpers `report` (`TST:245-247`), `rowIndex` (`:249-250`),
  `hlsRowsForFixture` (`:240-243`), plus this local row:
  `SubtitleTrackUIState(label = "", language = L, url = "", isForced = F, descriptiveLabel = N,
  playerTrackId = "subs:$N", playerTrackGroupId = "0:subtitle:$N", playerTrackIndex = 0, fromManifest = true)`.
  - `pickLanguageLessManifestRow_savesIdentityNotOff` (T-2a):
    1. `startedVM()`, then `report(listOf(<row L="", N="Signs", F=false>))`, then
       `clearMocks(interactor, answers = false)`.
    2. `SelectSubtitle(rowIndex(vm) { it.fromManifest })`.
    3. Assert `verify(exactly = 0) { interactor.saveTrackPreferences(42, any(), any(), null, null) }`.

    Red on base: G1 writes null/null.
  - `savedFullSubtitle_streamHasOnlyForcedRow_staysOff` (T-5b, base form per P-h):
    1. Stub `getPreferredSubtitleLang(42)` to return `"rus"` and `getPreferredSubtitleUrl(42)` to return `null`.
    2. `startedVM()`, then `report(listOf(<row L="rus", N="Russian Forced", F=true>))`.
    3. Assert `contentState(vm).selectedSubtitleIndex == 0` and
       `verify(exactly = 0) { playbackController.selectSubtitle(match { it.isForced == true }) }`.

    Red on base: G3 picks the forced row.
  - `savedSubtitleUnresolved_noPick_audioChangeKeepsStoredPreference` (T-16a):
    1. Stub lang `"rus"` and url `"https://test/subtitles/gone.vtt"`.
    2. `startedVM()`, then `report(hlsRowsForFixture())`. Two side-loaded rus rows make the language ambiguous,
       so nothing resolves.
    3. `clearMocks(interactor, answers = false)`, then `SelectAudioTrack(1)`.
    4. Assert `verify(exactly = 1) { interactor.saveTrackPreferences(42, any(), any(), "rus", "https://test/subtitles/gone.vtt") }`.

    Red on base: the save writes null/null.

  Run `--tests 'com.kino.puber.ui.feature.player.vm.PlayerVMTrackSelectionTest'`. Expect exactly these three to
  fail and the rest to pass. Paste the three failure lines into the Implementation record. Then remove the
  three tests (`git checkout -- <TST>`); they come back in the new form in Task 4.
- [x] Write first (repository, red by compile failure):
  - `subtitlePreference_roundTripsAllFourValues` (T-1b):
    1. Save `SubtitlePreference("eng", "subtitle:English SDH", true, "English SDH")`.
    2. Read it through a **new** `PlayerPreferencesRepository(fixture.context)`; all four values are equal.
    3. Save again with `isForced = false` and read back `false` (not null).
  - `subtitlePreference_noneClearsAllFourKeys` (T-3a):
    1. Save a full preference, then save `SubtitlePreference.NONE`.
    2. `values` holds none of `subtitle_lang_42`, `subtitle_url_42`, `subtitle_forced_42`, `subtitle_label_42`.
    3. The reader returns `NONE` (`isForced` is `null`).
  - `subtitlePreference_partialValuesRemoveOnlyNullKeys` (T-3c): after a full save, save `("rus", null, null, null)`.
    Only `subtitle_lang_42` remains.
  - `legacyKeysOnly_readAsLangUrlWithNullForcedAndLabel` (T-4a): seed `values["subtitle_lang_42"] = "rus"` and
    `values["subtitle_url_42"] = "https://test/subtitles/rus.vtt"`. The reader returns `("rus", <url>, null, null)`.

  Expose the `FakeSharedPreferences` in the test's `Fixture` data class (add a `preferences` field) so that
  `values` can be seeded and inspected. The upstream model for these is the `7d5addd2` repository test named above.
- [x] Create `SubtitlePreference.kt` exactly as under Produces.
- [x] Repository:
  - Add `KEY_SUBTITLE_FORCED_PREFIX = "subtitle_forced_"` and `KEY_SUBTITLE_LABEL_PREFIX = "subtitle_label_"`.
  - `getPreferredSubtitlePreference` reads lang and url with `getString(..., null)`, as `:16-22` do today.
  - It reads forced as `if (prefs.contains(key)) prefs.getBoolean(key, false) else null`, and label with `getString`.
  - `saveTrackPreferences(itemId, audioLang, audioLabel, subtitle)` keeps the single `prefs.edit {}`. For each of
    the four subtitle values it puts the value when non-null and removes the key when null (`putBoolean` for forced).
  - Delete the two old getters.
- [x] Interactor: `getPreferredSubtitlePreference(itemId)` and the new `saveTrackPreferences`, both plain
  delegation. Import `com.kino.puber.domain.model.SubtitlePreference`.
- [x] VM, behaviour-neutral (P-g):
  - In `restoreSubtitlePreference`, set `val saved = interactor.getPreferredSubtitlePreference(params.itemId)` and pass
    `preferredLang = saved.language, preferredUrl = saved.url` to the unchanged two-argument resolver.
  - In `saveTrackPreferences`, keep the `if (subtitleRestoredForCurrentMedia)` split (`:1856`).
    - The true branch builds `SubtitlePreference(language = <:1857 expr>, url = <:1858 expr>, isForced = null, descriptiveLabel = null)`.
    - The else branch is `interactor.getPreferredSubtitlePreference(params.itemId)`.
  - Call `interactor.saveTrackPreferences(itemId, audioLang, audioLabel, subtitle = ...)`.
- [x] Tests migration (P-d):
  - `FIX:107-108` → `every { interactor.getPreferredSubtitlePreference(any()) } returns SubtitlePreference.NONE`.
  - `FIX:116` → `every { interactor.saveTrackPreferences(any(), any(), any(), any()) } returns Unit`.
  - Add `stubSavedSubtitle` to `FIX`, next to `startedVM()` (`FIX:156`).
  - In `TST`, replace each stub pair `getPreferredSubtitleLang(42) returns A` / `getPreferredSubtitleUrl(42) returns B`
    with `stubSavedSubtitle(A, B)`.
  - Migrate the verifies:
    - Five-argument `any()` forms (98, 158, 174, 208, 375, 391, 431, 456, 504, 649, 663, 678) become four arguments with
      `any()` last. Line 98 becomes `(42, "rus", any(), any())`.
    - `(42, any(), any(), "rus", URL)` (414, 467, 519) becomes
      `(42, any(), any(), match { it.language == "rus" && it.url == URL })`.
    - `(…, null, null)` (415, 482) becomes `(…, SubtitlePreference.NONE)`.
- [x] Run the fast gate. Run the repository test and the player package tests: everything is green, and the four new
  repository tests pass.
- [x] Commit: `Persist the subtitle preference as one model with forced and label keys`.

**Done when:**
- `check.fast` is green.
- `--tests 'com.kino.puber.data.repository.PlayerPreferencesRepositoryTest' --tests 'com.kino.puber.ui.feature.player.*'`
  is green.
- `grep -rn 'getPreferredSubtitleLang\|getPreferredSubtitleUrl' app/src` prints nothing.
- The Implementation record holds the three red-on-base failures.

---

### Task 2: Add the subtitle preference key and identities helpers

**Covers:** AC-1, AC-2, AC-10 (key level), groundwork for AC-6
**Files:**
- Create: `M/ui/feature/player/vm/SubtitlePreferenceKey.kt`
- Create: `T/ui/feature/player/vm/SubtitleTestRows.kt` (P-c)
- Test (create): `T/ui/feature/player/vm/SubtitlePreferenceKeyTest.kt`

**Consumes:**
- `SubtitleTrackUIState` (`M/ui/feature/player/model/PlayerUIModels.kt:27-43`), with fields `url`, `sourceFile`,
  `playerTrackId`, `playerTrackGroupId`, `playerTrackUri`, `fromManifest`, `isForced` and `descriptiveLabel`.
- `isOff` (`:49-50`).
- `String.withoutMergedSourcePrefix()` (`M/ui/feature/player/vm/SubtitleTrackIdentity.kt:51`), which strips exactly one `^\d+:`.
- `SubtitleTrackUIState.readableDescriptiveLabel()` (`M/ui/feature/player/model/SubtitleLabeler.kt:93-97`). It returns
  null for a file-key label (`:100-105`) and for a label of three letters or fewer.
- `SubtitlePreference` (Task 1).

**Produces:**
```kotlin
// M/ui/feature/player/vm/SubtitlePreferenceKey.kt
internal fun SubtitleTrackUIState.subtitlePreferenceKey(): String?
//   first non-empty of: url, sourceFile,
//   playerTrackGroupId?.withoutMergedSourcePrefix() ONLY when fromManifest,
//   playerTrackUri, playerTrackId; null when all empty (never "")
internal val SubtitleTrackUIState.preferenceIdentities: List<String>
//   non-empty url, sourceFile, playerTrackUri, playerTrackId,
//   plus playerTrackGroupId and playerTrackGroupId.withoutMergedSourcePrefix() for fromManifest rows only (distinct)
internal fun SubtitleTrackUIState?.toSubtitlePreference(): SubtitlePreference
//   null or isOff -> SubtitlePreference.NONE; else
//   SubtitlePreference(language.takeIf { it.isNotEmpty() }, subtitlePreferenceKey(), isForced, readableDescriptiveLabel())

// T/ui/feature/player/vm/SubtitleTestRows.kt
internal fun manifestRendition(
    language: String, name: String, forced: Boolean = false, label: String? = name, merged: Boolean = true,
): SubtitleTrackUIState
//   label = "", url = "", isForced = forced, descriptiveLabel = label, playerTrackId = "subs:$name",
//   playerTrackGroupId = (if (merged) "0:" else "") + "subtitle:$name", playerTrackIndex = 0, fromManifest = true
```

- [x] Write first, in `SubtitlePreferenceKeyTest` (red by compile failure):
  - `keyOrder_urlThenSourceFileThenGroupIdThenTrackUriThenTrackId`: drop the fields one at a time and check
    the next one wins. Clearing them all gives `null`, not `""`.
  - `manifestRow_storesGroupIdWithoutMergePrefix` (AC-1): `manifestRendition("rus", "Rus")` gives the key `"subtitle:Rus"`.
    With `merged = false` the key is the same `"subtitle:Rus"`.
  - `sideLoadRow_neverStoresPositionalGroupId` (T-10a, AC-10): a side-load row has `fromManifest = false`,
    `playerTrackGroupId = "1:0"` and `playerTrackId = "rus.vtt"`.
    - With an API `url`, the key is the url.
    - Without a url but with a `sourceFile`, the key is the sourceFile.
    - Without either, the key is `"rus.vtt"`.
    - The key is never `"1:0"`, and `preferenceIdentities` never contains `"1:0"`.
  - `languageLessRow_givesNullLanguageNeverEmpty` (T-2c): `manifestRendition("", "Signs").toSubtitlePreference()` gives
    `language == null` and `url == "subtitle:Signs"`.
  - `readableLabelOnly_fileKeyLabelIsNotStored` (T-6e): a side-load whose `descriptiveLabel` equals its file key
    (`"rus.vtt"`) stores `descriptiveLabel = null`. `manifestRendition("eng", "English SDH")` stores `"English SDH"`.
  - `offOrNull_givesNone`: both `null.toSubtitlePreference()` and the Off row (`language = "", url = ""`) give `NONE`.
  - `manifestIdentities_includePrefixedAndStrippedGroupId`: `manifestRendition("rus", "Rus").preferenceIdentities`
    contains `"0:subtitle:Rus"`, `"subtitle:Rus"` and `"subs:Rus"`.
- [x] Implement `SubtitlePreferenceKey.kt` and `SubtitleTestRows.kt` as under Produces. Keep each function short:
  the key is a `listOf(...).firstOrNull { !it.isNullOrEmpty() }` chain, and there are no `return` guards.
- [x] Run the fast gate and `--tests 'com.kino.puber.ui.feature.player.vm.SubtitlePreferenceKeyTest'`, then the player package.
- [x] Commit: `Derive a stable preference key and identities for every subtitle row`.

**Done when:** `check.fast` is green and `SubtitlePreferenceKeyTest` (7 tests) plus the player package are green.

---

### Task 3: Resolve the saved subtitle by identity, label and a manifest-preferring language

**Covers:** AC-1, AC-2, AC-4, AC-5, AC-6, AC-7, AC-10 (resolver level)
**Files:**
- Modify: `RES` - `findSubtitleTrackIndex` (`:26-39`), `exactSubtitleUrlMatch` (`:98-104`) and `stableSubtitleUrlMatch`
  (`:106-112`), with the empty guard per P-a. Replace `unambiguousSubtitleLanguageMatch` (`:151-158`).
  `findSameSubtitleTrackIndex` (`:48-65`) and the audio matchers stay as they are.
- Test: `T/ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt`. Keep the existing `sideLoad` helper (`:13-20`) and
  the existing tests `:115-126` unchanged.

**Consumes:**
- From Task 2: `preferenceIdentities`, `readableDescriptiveLabel()` and `manifestRendition(...)`.
- `sameSubtitleIdentity` (`M/ui/feature/player/vm/SubtitleTrackIdentity.kt:13-23`). Plain equality comes first at `:14`.
  A group id has no `/` and no subtitle extension, so it never takes the path rule (`:25-31`).
- `sameSubtitleLanguage` (`M/ui/feature/player/model/SubtitleLanguage.kt:8-11`). It is false when either side is blank.
- `stableSubtitleKey` (`SubtitleTrackIdentity.kt:7-11`).

**Produces:**
```kotlin
fun findSubtitleTrackIndex(
    tracks: List<SubtitleTrackUIState>,
    preferredLang: String?,
    preferredUrl: String?,
    preferredIsForced: Boolean? = null,
    preferredDescriptiveLabel: String? = null,
): Int
```
Rules (spec D6/D7). The first rule that names a row wins.
- **0.** If `preferredLang == ""` and `preferredUrl.isNullOrEmpty()`, return `tracks.indexOfFirst { it.isOff }`.
  This is the only `return` guard.
- **1.** Exact `url == preferredUrl` over **all** rows, first index (P-a guard).
- **2.** Stable key of `url` over **all** rows, first index (P-a guard: an empty key gives no match).
- Candidates are `tracks.withIndex()` minus Off rows (P-b), filtered by compatibility. All three clauses must hold;
  write each clause as its own small private function so that `ComplexCondition` stays at 3 or fewer.
  - Language: `preferredLang.isNullOrEmpty() || row.language.isBlank() || sameSubtitleLanguage(row.language, preferredLang)`.
  - Forced: `preferredIsForced == null || row.isForced == null || row.isForced == preferredIsForced`.
  - Label: the normalised preferred label is null, **or** the row has no readable label, **or** the normalised
    labels are equal. Normalise with `trim().lowercase().takeIf { it.isNotEmpty() }`.
- **3.** The **single** candidate whose `preferenceIdentities.any { sameSubtitleIdentity(it, preferredUrl) }`.
  Skipped when `preferredUrl` is null or empty.
- **4.** The **single** candidate whose normalised `readableDescriptiveLabel()` equals the normalised preferred label.
  Skipped when the normalised preferred label is null (same kind of guard as rule 3). Without it, `null == null`
  matches a single unlabelled candidate whatever its identity, and T-7a and T-10c return 1 instead of `-1`.
- **5.** Language, which needs a non-empty `preferredLang`. The matches are the candidates with `sameSubtitleLanguage`.
  Return the single `fromManifest` match, otherwise the single match, otherwise `NO_MATCH`.

  This is not upstream's `playerTrackId != null`: fork side-loads carry a `playerTrackId` too (`:13-20`).
- Implement the rules as a `listOf({...})` matcher list, as at `:31-38`. Reuse the existing private `uniqueIndex` helper
  (`:67-69`) for "single candidate".

- [x] Write first (red by compile failure or assertion). Use `manifestRendition` and the existing `sideLoad`/`off`.
  - `findSubtitleTrackIndex_matchesManifestRowByStoredGroupId` (T-1c): `[off, eng manifest "English", eng manifest "English SDH"]`
    with `("eng", "subtitle:English SDH")` gives 2.
  - `findSubtitleTrackIndex_matchesPrefixedGroupIdAgainstStoredUnprefixedAndViceVersa` (T-1d):
    - A stored `"subtitle:Rus"` matches a merged row (`"0:subtitle:Rus"`) and an unmerged row.
    - A stored `"0:subtitle:Rus"` matches a merged row by equality.
  - `findSubtitleTrackIndex_preservesLanguageLessTrackByIdentity` (T-2b, ported from `f9c9ff38`). Cases over `[off, row]`
    with language-less rows, `preferredLang = null`, each giving 1:
    - a row with url `"https://test/unknown.vtt"`, stored as that url;
    - a row with `playerTrackId = "unknown"`, stored as `"unknown"`;
    - `manifestRendition("", "Unknown")`, stored as `"subtitle:Unknown"`.

    Also run the same three cases with `preferredLang = ""` and the same identity (rule 0 must not fire).
  - `findSubtitleTrackIndex_legacyPreference_resolvesToSameRowAsBefore` (T-4b, AC-4). It is a table, with forced and
    label null, and each expected index is what the base resolver returns:
    - an exact API URL;
    - the same file from another host or with a new token (stable key);
    - a single-language row;
    - a URL that matches a forced row whose language differs from the preference. Rules 1-2 are unfiltered, so the
      row is still found.
  - `findSubtitleTrackIndex_legacyAmbiguousLanguage_nowResolvesToSingleManifestRow` (T-4c, reworded AC-4):
    - `[off, sideLoad("rus", "a.srt", 1), manifestRendition("rus", "Rus")]` with `("rus", null)` gives 2.
    - Two rus manifest rows give -1.
  - `findSubtitleTrackIndex_rejectsForcedOnlyReplacementForFullPreference` (T-5a, ported from `7d5addd2`, AC-5):
    `[off, manifestRendition("rus", "Forced", forced = true)]` with `("rus", null, false, null)` gives -1.
  - `findSubtitleTrackIndex_usesForcedFlagToChooseBetweenFullAndForcedSameLanguage` (T-6a, AC-6). The rows are two rus
    manifest rows, label null, forced false and true:
    - `forced = false` picks the full row;
    - `forced = true` picks the forced row.
  - `findSubtitleTrackIndex_usesMeaningfulLabelToResolveSameLanguageVariants` (T-6b, ported from `7d5addd2`). The rows are
    two eng rows with `playerTrackId = "reused"`, forced false, and labels `"English SDH"` / `"English Commentary"`.
    `("eng", "reused", false, "English Commentary")` gives the Commentary index.
  - `findSubtitleTrackIndex_labelComparisonIsTrimmedAndCaseInsensitive` (T-6c): the label `"  english commentary "` matches.
  - `findSubtitleTrackIndex_rowWithoutReadableLabelPassesLabelFilter` (T-6d, R4): `[off, API rus row with url, no label]`
    with `("rus", "subtitle:Russian Full", false, "Russian Full")` gives 1, through rule 5.
  - `findSubtitleTrackIndex_rejectsReusedIdentityWithDifferentLanguage` (ported from `7d5addd2`): a spa row with
    `playerTrackId = "reused-id"` and `("eng", "reused-id")` gives -1.
  - `findSubtitleTrackIndex_doesNotRestoreOffWhenLanguageLessIdentityDisappears` (T-7a, ported from `f9c9ff38`, AC-7):
    - `[off, language-less row with playerTrackId "other"]` with `(null, "missing")` gives -1;
    - with `("", "missing")` it also gives -1.
  - `findSubtitleTrackIndex_emptyLanguageWithoutIdentity_meansOff` (T-7b): `("", null)` and `("", "")` both give 0.
  - `findSubtitleTrackIndex_positionalSideLoadGroupIdNeverMatches` (T-10c): `[off, sideLoad("", "a.srt", 1)]` with
    `(null, "1:0")` gives -1.
- [x] Implement the rules in `RES`. Leave the existing tests `:115-126` untouched; they must stay green.
- [x] Run the fast gate and `--tests 'com.kino.puber.ui.feature.player.vm.AudioTrackPreferenceResolverTest'`, then the
  player package. `TST` must stay green: the VM still passes two fields, and every existing VM restore case resolves
  as before (checked: `TST:149-160` and `:641-651` use non-manifest rows only).
- [x] Commit: `Resolve the saved subtitle by identity, readable label and a manifest-preferring language`.

**Done when:** `check.fast` is green, `AudioTrackPreferenceResolverTest` (old and new) is green, and the player package
is green.

---

### Task 4: Save the full preference, restore the live intent first, and keep the stored value after an unresolved restore

**Covers:** AC-1, AC-2, AC-3, AC-5, AC-8, AC-9, AC-10, AC-16 (VM level); AC-12 (full gate)
**Files:**
- Modify: `VM`. The line numbers are from before Task 1, which moved `:599-616` and `:1849-1870` by a few lines.
  - Add the field `private var subtitlePickedThisMedia = false` next to `subtitleRestoredForCurrentMedia` (`:165`).
  - `restoreSubtitlePreference` (`:599-616`) changes.
  - In `applySubtitleSelection` (`:869-882`), inside `if (persist)`, set `subtitlePickedThisMedia = true` next to
    `subtitleRestoredForCurrentMedia = true` (`:879`).
  - In `resetMediaSession` (`:978-997`), set `subtitlePickedThisMedia = false` next to `:994`.
  - `saveTrackPreferences` (`:1849-1870`) changes.
  - Leave `onTracksUpdated` (`:535-579`), `selectBufferPreset` (`:927-939`) and `toggleFastDns` (`:941-951`) unchanged.
    A rebuild keeps both flags as they are, because it is the same media.
- Test: `TST`

**Consumes:**
- From Task 2: `toSubtitlePreference()`.
- From Task 3: the resolver's four-argument `findSubtitleTrackIndex`.
- From Task 1: `interactor.getPreferredSubtitlePreference`, `stubSavedSubtitle`, and `manifestRendition` (Task 2).
- `intendedSubtitleRow` (`VM:187`). It is re-found by `onTracksUpdated` through `findSameSubtitleTrackIndex` (`:545-550`).
- `playbackController.selectSubtitle`.

**Produces** (behaviour; no new public API):
```kotlin
private fun restoreSubtitlePreference() {
    val content = (stateValue as? PlayerViewState.Content)?.content ?: return
    val shown = content.subtitleTracks.getOrNull(content.selectedSubtitleIndex)
    // D9: on a rebuild onTracksUpdated has already re-found the live intent; it wins over the saved value.
    if (intendedSubtitleRow != null && shown != null && !shown.isOff) {
        playbackController.selectSubtitle(shown)
        return
    }
    val saved = interactor.getPreferredSubtitlePreference(params.itemId)
    val index = audioTrackPreferenceResolver.findSubtitleTrackIndex(
        tracks = content.subtitleTracks, preferredLang = saved.language, preferredUrl = saved.url,
        preferredIsForced = saved.isForced, preferredDescriptiveLabel = saved.descriptiveLabel,
    )
    if (index >= 0) {
        applySubtitleSelection(index = index, persist = false)
        return
    }
    // keep the existing comment of :612-614
    shown?.let(playbackController::selectSubtitle)
}

// saveTrackPreferences: subtitle part
val subtitle = if (subtitleRestoredForCurrentMedia && (intendedSubtitleRow != null || subtitlePickedThisMedia)) {
    intendedSubtitleRow.toSubtitlePreference()          // D8 + explicit Off -> NONE (AC-3)
} else {
    // pending restore, or a restore that found nothing with no pick since: keep what is stored (AC-16)
    interactor.getPreferredSubtitlePreference(params.itemId)
}
```
This has three returns (the `?: return` guard plus two) and three conditions, so it passes `ReturnCount` and
`ComplexCondition`.

- [x] Write first, all in `TST` with the new signature. Use `manifestRendition` from `SubtitleTestRows.kt` and
  `SubtitlePreference` (import `com.kino.puber.domain.model.SubtitlePreference`).
  1. Add the three failing-first tests and run them. **All three must be red** before any VM edit (P-g); record the failures.
     - T-2a `pickLanguageLessManifestRow_savesIdentityNotOff`:
       1. `report(listOf(manifestRendition("", "Signs")))`, then `clearMocks(interactor, answers = false)`.
       2. Pick that row.
       3. `verify(exactly = 1) { interactor.saveTrackPreferences(42, any(), any(), match { it.language == null && it.url == "subtitle:Signs" }) }`
          and `verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), SubtitlePreference.NONE) }`.
     - T-5b `savedFullSubtitle_streamHasOnlyForcedRow_staysOff` (final form per P-h):
       1. `stubSavedSubtitle("rus", null, isForced = false)`, then
          `report(listOf(manifestRendition("rus", "Russian Forced", forced = true)))`.
       2. Assert the selected index is 0 and `verify(exactly = 0) { playbackController.selectSubtitle(match { it.isForced == true }) }`.
     - T-16a `savedSubtitleUnresolved_noPick_audioChangeKeepsStoredPreference`:
       1. `stubSavedSubtitle("rus", "https://test/subtitles/gone.vtt")`, then `report(hlsRowsForFixture())`.
       2. `clearMocks(interactor, answers = false)`, then `SelectAudioTrack(1)`.
       3. `verify(exactly = 1) { interactor.saveTrackPreferences(42, any(), any(), SubtitlePreference("rus", "https://test/subtitles/gone.vtt", null, null)) }`.
  2. Add the remaining VM tests. Some may already pass (T-9 is a regression pin); note which ones in the record.
     - T-1a `pickManifestOnlyRow_savesUnprefixedGroupIdWithLanguageForcedAndLabel` (AC-1):
       1. `report(hlsRowsForFixture() + manifestRendition("eng", "English SDH"))`.
       2. Pick the eng row.
       3. Verify a save with `SubtitlePreference("eng", "subtitle:English SDH", false, "English SDH")`; no `"0:"` appears in the url.
     - T-1e `newSession_restoresManifestOnlyRowFromSavedPreference` (AC-1, AC-2 re-entry):
       1. `stubSavedSubtitle("eng", "subtitle:English SDH", false, null)`.
       2. Report `manifestRendition("eng", "English") + manifestRendition("eng", "English SDH")`.
       3. Expect `selectSubtitle(match { it.playerTrackGroupId == "0:subtitle:English SDH" })` and zero saves.

       Add a language-less variant: `stubSavedSubtitle(null, "subtitle:Signs")` with `manifestRendition("", "Signs")`
       selects that row.
     - T-3b `explicitOff_savesNoneAndClearsAllValues` (AC-3):
       1. Pick the rus.vtt row, then `clearMocks(interactor, answers = false)`, then `SelectSubtitle(0)`.
       2. `verify(exactly = 1) { interactor.saveTrackPreferences(42, any(), any(), SubtitlePreference.NONE) }`.
     - T-8a `rebuild_intentDiffersFromSaved_resolvesIntentFirst` (AC-8):
       1. `rows = hlsRowsForFixture() + manifestRendition("eng", "English")`. Report `rows`, pick rus.vtt, then
          `stubSavedSubtitle("eng", "subtitle:English")`.
       2. `ToggleFastDns`, then `clearMocks(playbackController, interactor, answers = false)`, then report `rows`.
       3. Expect exactly 1 `selectSubtitle(match { it.playerTrackId == "rus.vtt" })`, 0
          `selectSubtitle(match { it.language == "eng" })`, and 0 saves (`any()` in all four slots).
       4. This is red on base and stays red until the D9 branch exists.
     - T-8b `newMedia_intentIsNull_usesSavedPreferenceOnly`:
       1. Pick rus.vtt, then `clearMocks(interactor, answers = false)` (the pick itself saves), then
          `SelectEpisode(1, 2)`, then `stubSavedSubtitle("eng", "subtitle:English")`.
       2. Report `rows`; the eng row is selected and nothing is saved (`verify(exactly = 0)`, `any()` in all four slots).
     - T-8c `rebuild_intentRowMissing_fallsBackToSavedPreference` (R5 pin):
       1. Pick rus.vtt, then `clearMocks(interactor, answers = false)` (the pick itself saves), then
          `stubSavedSubtitle("eng", "subtitle:English")`.
       2. `ToggleFastDns`, then report `listOf(manifestRendition("eng", "English"))`.
       3. Expect `selectSubtitle(eng)` once and zero saves (`verify(exactly = 0)`, `any()` in all four slots).
     - T-9 `rebuildFastDns_audioEmptyReportWithTextRows_reSendsIntent_andSavesNothing` (AC-9):
       1. Use `pickedRussianWithSavedPreference()` (`TST:394-398`), then `ToggleFastDns`, then `report(rows, audio = emptyList())`.
       2. Expect exactly 1 `selectSubtitle(any())`, and it is the rus.vtt row.
       3. Expect zero saves, and `contentState(vm).audioTracks == testContentState.audioTracks`.
     - T-10b `pickSideLoadedRow_savesUrlNotGroupId` (AC-10):
       1. `report(hlsRowsForFixture())`, then pick rus.vtt.
       2. Verify a save with `match { it.url == testSubtitleTracks[1].url }`.
       3. Verify 0 saves with `match { it.url?.matches(Regex("""\d+:0""")) == true }`.
     - T-16b `savedSubtitleUnresolved_pickAfterUnresolved_replacesStored`: start as in T-16a, then pick rus.vtt.
       The save carries `match { it.url == testSubtitleTracks[1].url }`.
     - T-16c `savedSubtitleUnresolved_explicitOffAfterUnresolved_clearsStored`:
       1. Start as in T-16a, then `SelectSubtitle(0)`. The save is `SubtitlePreference.NONE`.
       2. Then `SelectEpisode(1, 2)` and `report(hlsRowsForFixture())` (unresolved again), `clearMocks(interactor, answers = false)`
          and `SelectAudioTrack(1)`. The save passes the stored `("rus", gone, null, null)` through. This checks that the
          flag resets in `resetMediaSession`.
- [x] Implement the VM changes under Produces, and set and reset the flag as listed under Files.
- [x] Run the fast gate and `--tests 'com.kino.puber.ui.feature.player.vm.PlayerVMTrackSelectionTest'`. All tests are
  green, including the rebuild tests `:401-457` (AC-8) and the migrated verifies.
- [x] Run the full gate (AC-12): `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
  Fix any detekt finding by restructuring, not by suppression.
- [x] Commit: `Save the full subtitle preference and restore the live intent before the saved one`.

**Done when:** `check.fast` and `check.full` are green, and the Implementation record lists the three tests that were red
before the VM edit and green after it.

---

### After Task 4 (not an implementer task)

- qa-device runs the spec's manual checks M1-M6 (AC-11) on a leased emulator with the worktree's own dev APK,
  installed with `tools/android-apk-install-preserve`.
- AC-11 quality-switch clause: qa-device runs the spec's Test design section 5 **M3** as reworded for AC-11 (Q4).
  It is a pass/fail check of AC-11, not an observation:
  - (a) Pick a subtitle row, switch to a quality whose stream also offers that row: the row stays on.
  - (b) Switch to a quality whose stream lacks the row (manual quality is `url.hls` without manifest subtitles,
    for example Outlander S1E5 720p): the picker shows Off, and Off is **not** saved. Switch back to Auto: the row
    is on again. Exit the player and re-enter: the row is on (this proves Off was not saved).
  - If no title with such a stream can be found, the qa report marks (b) `NOT CHECKED` with the reason. It must not
    be passed by observation, and (a) alone does not pass AC-11.
- No extra code is needed for (b): `onTracksUpdated` keeps the intent when the row is missing (`VM:545-550`), and the
  Task 4 save writes the non-null intent.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** (one per task; shas in `git log f351d988..HEAD`)

1. `Persist the subtitle preference as one model with forced and label keys` (Task 1)
2. `Derive a stable preference key and identities for every subtitle row` (Task 2)
3. `Resolve the saved subtitle by identity, readable label and a manifest-preferring language` (Task 3)
4. `Save the full subtitle preference and restore the live intent before the saved one` (Task 4, with this record)

**Red runs (failing-first):**

- On base `f351d988`, old signature, `PlayerVMTrackSelectionTest` (47 tests, exactly these three failed):
  - `pickLanguageLessManifestRow_savesIdentityNotOff`: `java.lang.AssertionError at PlayerVMTrackSelectionTest.kt:268` (the base wrote null/null).
  - `savedFullSubtitle_streamHasOnlyForcedRow_staysOff`: `org.opentest4j.AssertionFailedError at PlayerVMTrackSelectionTest.kt:286` (selected index was the forced row).
  - `savedSubtitleUnresolved_noPick_audioChangeKeepsStoredPreference`: `java.lang.AssertionError at PlayerVMTrackSelectionTest.kt:300`.
- New signature, before the VM edit of Task 4 (resolver already new, VM still two-field and old save), 6 of the 58 VM tests failed:
  - the three above (`PlayerVMSubtitlePreferenceTest`-era names are the same; `pickLanguageLessManifestRow...:254`, `savedFullSubtitle...:269`, `savedSubtitleUnresolved_noPick...:287` in the then file);
  - `savedSubtitleUnresolved_explicitOffAfterUnresolved_clearsStored` (AC-16 flag reset), `rebuild_intentDiffersFromSaved_resolvesIntentFirst` (T-8a), `pickManifestOnlyRow_savesUnprefixedGroupIdWithLanguageForcedAndLabel` (T-1a).
  - All six are green after the VM edit.
- Already green before the VM edit (regression pins or resolver-only behaviour): T-1e (both variants), T-3b, T-8b, T-8c, T-9, T-10b, T-16b.

**Deviations from the plan:**

- The new VM tests live in a new class `PlayerVMSubtitlePreferenceTest`, not in `PlayerVMTrackSelectionTest`. Detekt `LargeClass` (an `AC-12` gate) flagged `PlayerVMTrackSelectionTest` once it grew by the new cases, and the plan forbids suppressions. To share the helpers, `audioReport`, `sideLoad`, `hlsRowsForFixture`, `report` and `rowIndex` moved from the test class into `PlayerVMTestFixture` as `protected` (bodies unchanged).
- T-9 does not use `pickedRussianWithSavedPreference()` (it stays private in `PlayerVMTrackSelectionTest`); it builds the same state inline with `manifestRendition("eng", "English")` as the extra row.
- The three base-red tests were first written with the old signature as the plan says (their Task 1 form), then reverted; the Task 4 forms use a small `unresolvedSavedSubtitleVM()` helper shared by T-16a..c.
- `PlayerVM` keeps no import of `SubtitlePreference` (Task 4 produces it through `toSubtitlePreference()`); Task 1 imported it for the behaviour-neutral save and Task 4 removed it again.

**Files touched outside the plan:**

- `PlayerVMTestFixture.kt` (shared helpers moved there, see above) and the new `PlayerVMSubtitlePreferenceTest.kt`.

**For the reviewers:**

- `AudioTrackPreferenceResolver.findSubtitleTrackIndex`: rule 0 is the only guard return; rules 1-2 use the P-a empty guard and run over all rows; rules 3-5 run over `candidates` (Off rows excluded, P-b). Rule 4 is skipped for a null label.
- `PlayerVM.restoreSubtitlePreference` D9 branch and the `hasIntent` condition in `saveTrackPreferences` (AC-16: intent or an explicit pick this media).
- Full gate: `testDevDebugUnitTest` failed twice or three times in a row in the first full runs on unrelated suites (`HomeVMIncrementalPublishTest`, `HistoryVMDeletionSchedulingTest`; `UncaughtExceptionsBeforeTest`, a coroutine that touched `Dispatchers.Main` after another test reset it). Each passed when rerun alone, and two further `--rerun-tasks` full runs plus the final `check.full` run were green. I judge it a pre-existing order/load flake and did not touch it; reviewers may want to know it exists.
- `:app:detektAll` printed "83 compiler errors found during analysis" as a warning in one run; it did not fail the task.
- Device check AC-11 (M1-M6, steps 3 and 4 of spec section 5) is still open for qa-device.

**Blocked:** none.

## Readiness

### Round 1

# Readiness 1 - subtitle-restore-model

Judge: final-judge, mode readiness, round 1. Worktree HEAD `62d24119` (plan), code base `f351d988`.
`git diff --stat f351d988 HEAD -- app` is empty, so every `path:line` was checked against the base code.

## Verdict: NOT READY

One blocking gap: the device design for the reworded part of AC-11 is missing. Everything else is ready.

## AC coverage (plan task + Test design row)

| AC | Plan task | Test design rows | Status |
|----|-----------|------------------|--------|
| AC-1 | T1 (repository), T2 (key), T3 (resolver), T4 (VM) | T-1a..T-1e | covered |
| AC-2 | T2, T3, T4 | T-2a..T-2c, T-1e language-less variant | covered |
| AC-3 | T1, T4 | T-3a..T-3c | covered |
| AC-4 | T1, T3 | existing `:115-126`, T-4a..T-4c | covered |
| AC-5 | T3, T4 | T-5a, T-5b (base form per P-h) | covered |
| AC-6 | T2, T3 | T-6a..T-6e | covered |
| AC-7 | T3 | T-7a, T-7b | covered |
| AC-8 | T4 | T-8a..T-8c, existing `TST:401-457` | covered |
| AC-9 | T4 | T-9 | covered |
| AC-10 | T2, T3, T4 | T-10a..T-10c | covered |
| AC-11 | "After Task 4" (qa-device) | M1-M6, section 5 | **partial**: re-entry is designed (M1, M2, M4, M6); the reworded quality-switch clause is not (see fix 1) |
| AC-12 | T4 full gate | AC-12 row | covered |
| AC-16 | T4 | T-16a..T-16c | covered |

## Citations sampled (all resolve at `f351d988`)

- `PlayerVM.kt:165` `subtitleRestoredForCurrentMedia`; `:187` `intendedSubtitleRow`; `:545-550` intent re-find in `onTracksUpdated`; `:599-616` `restoreSubtitlePreference`; `:869-882` `applySubtitleSelection` (`:875` intent, `:879` flag); `:936-937`, `:948-949` flag resets on rebuild; `:994-995` in `resetMediaSession`; `:1849-1870` `saveTrackPreferences` (`:1857-1858` `takeIf { isNotEmpty }`).
- `AudioTrackPreferenceResolver.kt:26-39` `findSubtitleTrackIndex`; `:48-65` `findSameSubtitleTrackIndex`; `:67-69` `uniqueIndex`; `:98-104` exact URL (`:102` null-only guard, so P-a is real); `:106-112` stable key; `:151-158` `unambiguousSubtitleLanguageMatch`.
- `SubtitleTrackIdentity.kt:7-11` `stableSubtitleKey`, `:13-23` `sameSubtitleIdentity` (equality at `:14`), `:25-31` path rule, `:51` `withoutMergedSourcePrefix`.
- `PlayerPreferencesRepository.kt:16-22` getters, `:28-57` save, `:121-122` keys; `PlayerInteractor.kt:335-358`.
- `PlayerVMTestFixture.kt:107-108,116` stubs, `:156` `startedVM`; `PlayerVMTrackSelectionTest.kt:240-243`, `:245-247` (`report` takes `audio`), `:249-250`, `:394-398`; `collapsedAfterPick` clears mocks (`:360`), which T-9 relies on.
- P-d counts were re-counted with grep: 17 verifies at the listed lines, 15 stub pairs at the listed lines. No other caller of the two getters or `saveTrackPreferences` exists under `app/src`.
- `FakeSharedPreferences.kt:16,25-59` (contains, getBoolean, putBoolean, remove, apply); `PlayerPreferencesRepositoryTest.kt:33-44` fixture; `SubtitleLanguage.kt:8-11`; `PlayerTextTracks.kt:48`; `Models.kt:434`; `PlayerUIMapper.kt:47`; `PlaybackMediaItemFactory.kt:45,51-52`; `app/build.gradle.kts:340` (detektAll covers unit tests).
- Detekt: `detekt.yml` `ComplexCondition.allowedConditions: 3` (detekt `2.0.0-alpha.6`), `ReturnCount.max: 3` with `excludeGuardClauses: false`. The Task 4 sketch has 3 conditions and 3 returns: within limits.
- Small drift, not blocking: the spec cites `PlaybackController.kt:822` for the early return; `:822` is the text-group filter and the return is `:823`.

## Failing-first and ordering

- Base red is real: the merger keeps only player rows (`SubtitleTrackMerger.kt:16-30`), so T-5b base sees `[Off, forced rus]` and the legacy language rule picks the forced row; T-2a base writes null/null (G1); T-16a base sees two side-load rus rows, ambiguous, then writes null/null.
- Task order: T1 (signature + all callers + tests in one commit, behaviour-neutral) -> T2 (additive helpers) -> T3 (resolver with defaulted new params, VM still two-arg) -> T4 (VM). No task leaves compile or tests red.
- Scope: no change to `PlaybackController`/`prepare()`, merger, selector, `findSameSubtitleTrackIndex`, DI, UI or strings. Inside Out of scope.
- Open questions Q1-Q4 answered and recorded in Decisions.

## Must fix

1. **AC-11 quality-switch clause has no device design.** AC-11 was reworded at gate 1 (Q4): after a quality switch the row is on when the new stream offers it; on a stream without it the picker shows Off **without saving Off**, and the pick comes back on Auto and on re-entry. Test design section 5 still has the pre-rework M3, which treats the switch as "observation only" and says an Off result is "not a failure of this run". That lets an AC-11 failure through the gate. Update section 5 (and the plan's "After Task 4" pointer) with steps for: (a) a quality whose stream has the row -> still on; (b) a quality whose stream lacks it -> picker Off, then back to Auto -> row on again, then exit and re-enter -> row on (proves Off was not saved). Remove the "not a failure of this run" escape for the AC-11 clause. If no title with a stream that lacks the row can be found, say that the qa report must mark sub-clause (b) `NOT CHECKED` and give the reason. It must not be passed by observation.

## Advisory (not blocking; tests would catch them)

- Task 3 rule 4: skip it when the normalised preferred label is null. Otherwise `null == null` matches one unlabelled candidate whatever its identity, and T-7a and T-10c would return 1 instead of -1. Rule 3 states its guard; rule 4 should state the same kind of guard.
- T-8b and T-8c assert "zero saves" after an earlier explicit pick, which saves. They need `clearMocks(interactor, answers = false)` after the pick, as T-8a does.

### Round 2

# Readiness 2 - subtitle-restore-model

Judge: final-judge, mode readiness, round 2. Worktree HEAD `055d22be`, code base `f351d988`.
`git diff --stat f351d988 HEAD` touches only the spec and the plan (no `app/` change), so the round 1 citation
sample still holds against the base code. Round 2 reviews the `62d24119..055d22be` delta.

## Verdict: READY

## Round 1 must-fix

1. **AC-11 quality-switch clause has no device design: fixed.**
   - Spec section 5 step 3 is now clause (a): a quality that keeps the row means the row must stay on. An Off or
     wrong row "FAILS AC-11; it is a failure of this run, not an observation". The old escape is gone.
   - Step 4 is clause (b): the stream lacks the row (manual quality `url.hls`, Outlander S1E5 720p, checked through
     stream info). The picker must show Off, then (4b) Auto brings the row back, then (4c) re-entry shows the row,
     which proves Off was not saved.
   - If no such stream is found, (b) is marked `NOT CHECKED` with the titles tried. The plan says it cannot pass by
     observation or by (a).
   - The Test design AC-11 row now names steps 3 and 4 and the `NOT CHECKED` rule.
   - The plan's "After Task 4" section has the same (a)/(b) steps and says "(a) alone does not pass AC-11".
   - The plan relies on `VM:545-550` for (b): when the row is missing, `onTracksUpdated` shows Off and leaves
     `intendedSubtitleRow` alone. Checked in the code: it does.

## Round 1 advisories

- Task 3 rule 4 now says "Skipped when the normalised preferred label is null (same kind of guard as rule 3)".
  It also gives the reason (T-7a, T-10c). Addressed.
- T-8b and T-8c now call `clearMocks(interactor, answers = false)` after the pick, before the step under test. They
  assert zero saves with `verify(exactly = 0)` and `any()` in all four slots. Addressed.

## Coverage, ordering, scope (unchanged since round 1)

- Every AC (AC-1..AC-12, AC-16) has a plan task and a Test design row. AC-11 is now fully designed.
- T1 -> T2 -> T3 -> T4 is in dependency order, and no task leaves the build red.
- The plan stays inside Out of scope. Q1-Q4 are answered.

## Advisory (not blocking)

1. Spec section 5 step 4 says to check the save in "the player's subtitle-restore log line ... filtered by tag".
   No such log exists at base: `PlayerVM.kt` has no `Log.*` call, and `saveTrackPreferences` (`:1849-1870`) does
   not log. The plan adds no log. So qa-device cannot use that line. Step 4c (re-entry shows the row) is the
   evidence that Off was not saved, and it does not need the log. qa should not report the log check as done.
2. Naming drift: the plan's pointer says "the reworded M3". The spec splits the quality switch into steps 3 (a)
   and 4 (b, 4b, 4c). The Test design row names both steps. qa should follow steps 3 and 4 of section 5.
3. Carried from round 1: the spec cites `PlaybackController.kt:822` for the early return, which is at `:823`.

## Must fix

None.
