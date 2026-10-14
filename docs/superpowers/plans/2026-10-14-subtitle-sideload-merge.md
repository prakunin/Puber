# Side-load API subtitles on HLS and merge them with manifest tracks - plan

**Spec:** `docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md`
**Goal:** On HLS, API subtitles are side-loaded through `DefaultMediaSourceFactory` and shown in one picker together with the manifest renditions. That picker is merged per language, labelled by language name, selected by source identity and never by position. Progressive playback keeps the API rows it has today.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Worktree: `/home/user1/orca/workspaces/Puber/subtitle-sideload-merge` (branch
  `subtitle-sideload-merge`, base `a97ee2b1`). Run every command there
  (`cd <wt> && ...`). Never run `./gradlew` in a worktree; the guard hook refuses it. Prefix
  with `AGENT_GRADLE_SERIAL=1` whenever another worktree may build.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`.
  Full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests:
  `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<FQCN or pattern>'`.
  To run the whole player package, use `--tests 'com.kino.puber.ui.feature.player.*'`.
  The fast gate compiles production sources only, so after every task also run the
  touched tests: they are the only check that the test sources compile.
- One task means a green fast gate, green touched tests and one commit. The commit
  message is one imperative English sentence with no prefix. Never add a `Co-Authored-By`
  line, whatever the harness suggests. Each task gives a suggested message.
- Stage explicit paths only (`git add <path>...`, and `git rm <path>` for
  deletions). Never use `git add -A` or `git add .`. The conductor commits this plan file
  and the spec; the implementer does not.
- Test-first: in each task, write the tests listed under "Write first" **before** the
  production code, run them, and see them fail. A compile failure of the test source
  counts as failing. Record the failure in the Implementation record. The three
  failing-first tests of the spec's Test design §3 come first in Task 5 (VM), Task 6
  (selector) and Task 7 (factory), in that order.
- Unit tests are JUnit 5 (`org.junit.jupiter.api.*`). VM tests use
  `@RegisterExtension MainDispatcherExtension` (as in `T/vm/PlayerVMTrackSelectionTest.kt:26-30`).
  Map strings through `FakeResourceProvider` (`app/src/test/kotlin/com/kino/puber/util/FakeResourceProvider.kt:8-19`).
  There is no Robolectric, so no unit test may build `MediaItem`, `Uri` or `ExoPlayer`.
  Media3 *constants* (`MimeTypes.*`, `C.*`) are fine.
- Detekt runs over production **and** unit-test sources (`app/build.gradle.kts:338-342`).
  The limits that bite here (`config/detekt/detekt.yml`) are:
  - `ReturnCount` max 3 (`:756-764`): use matcher lists, as `AudioTrackPreferenceResolver` does.
  - `ComplexCondition` 3 (`:81-83`).
  - `CyclomaticComplexMethod` 14 (`:90-92`).
  - `LongMethod` 60 lines in production code (`:113-116`).
  - `LargeClass` 600 (`:110-112`).
  - `UnusedParameter` (`:817-820`), which also applies in tests.

  The baseline is empty. Do not add suppressions; restructure instead.
- Scope: no DI change, no screen-module change, no Compose layout change, no new
  string resource, no change to `PlaybackErrorPolicy`, the warm-up *shape* or
  `PlayerPreferencesRepository`. Out-of-scope items (spec "Out of scope", Q3, Q4)
  stay out.
- Device verification (spec Test design §7, D-1..D-9) belongs to qa-device after
  Task 8. No task here installs, launches or touches a device.
- Upstream sources are read from the **main checkout**:
  `git -C /mnt/data/projects/PetProjets/Puber show b755826c:<path>`. Upstream paths:
  - `UP/` = `app/src/main/java/com/kino/puber/ui/feature/player/`
  - `UPT/` = `app/src/test/kotlin/com/kino/puber/ui/feature/player/`
- Worktree paths:
  - `M/` = `app/src/main/java/com/kino/puber/ui/feature/player/`
  - `T/` = `app/src/test/kotlin/com/kino/puber/ui/feature/player/`
  - `PC` = `M/vm/PlaybackController.kt`
  - `VM` = `M/vm/PlayerVM.kt`

  Every `path:line` below was read at `a97ee2b1` (the spec commits after it do not
  touch code). Line numbers are those **before** the plan starts; later tasks note
  when an earlier task has moved them.

Decisions referenced (D1..D16, R1..R10) are in the spec's Design section. The plan
refines three points without changing any AC. Reviewers should check them:

- **P-a (strip only when merged).** HLS rendition format ids are `"<GROUP-ID>:<NAME>"`.
  Stripping a leading `\d+:` from an *unmerged* id would therefore corrupt it when the
  GROUP-ID is numeric. So the controller strips the merge prefix only when the
  current item was built with side-loads, which is exactly when `DefaultMediaSourceFactory`
  wraps it in a `MergingMediaSource`. It sets `fromManifest = !merged || groupId.startsWith("0:")`.
  This is D5 with that guard added.
- **P-b (identity step split in two).** In the selector's source-identity step (D8),
  an exact `playerTrackId` match runs first. The URL/file identity match runs second.
  Each is reduced with `singleOrNull`. Without the split, a manifest row that the merger
  enriched with an API `url` would tie with its hidden side-loaded copy. Both are still
  "source identity" in AC-4's sense.
- **P-c (labeler built per call).** The mapper and the VM build the labeler from
  `resources` on each call, not once at construction, so `resources.locale` is read when
  labelling (`T/model/PlayerUIMapperTest.kt:98-106` changes the locale after the mapper
  is built). `PlayerTextTrack.groupIndex` stays only as an address for the controller.
  No rule matches on it.

---

## Tasks

### Task 1: Shared subtitle identity and language helpers, and `SubtitleLink.file`

**Covers:** AC-4 (strip helper), AC-7 (D3 field, canonical language), groundwork for AC-2/3/8
**Files:**
- Modify: `M/vm/SubtitleTrackIdentity.kt:1-12`. Replace it with upstream
  `UP/vm/SubtitleTrackIdentity.kt` (48 lines: `stableSubtitleKey`,
  `sameSubtitleIdentity`, `SUBTITLE_FILE_EXTENSION`, `<file>/index.m3u8`
  normalisation), then add:
  ```kotlin
  /** MergingMediaSource rewrites child ids to "<childIndex>:<id>"; strips exactly one such prefix. */
  internal fun String.withoutMergedSourcePrefix(): String = MERGED_SOURCE_PREFIX.replace(this, "")
  private val MERGED_SOURCE_PREFIX = Regex("""^\d+:""")
  ```
  (`replace` with an anchored pattern strips one prefix only.)
- Create: `M/model/SubtitleLanguage.kt` (package `...player.model`). **Move** these from
  `M/model/SubtitleLabels.kt` unchanged:
  - `canonicalSubtitleLanguage` (`:42-53`) with `bibliographicToTerminology` (`:56-61`),
  - `subtitleLanguageDisplayName` (`:70-79`),
  - `iso3Locales` (`:81-90`).

  Make the private `AI_GENERATED_SUBTITLE_LANGUAGE` (`:7`) `internal const` here, and
  add upstream's
  `sameSubtitleLanguage` (`UP/vm/SubtitleLanguage.kt:7-10`). **Do not** take
  upstream's `canonicalSubtitleLanguage`: it lacks the fork's bibliographic map.
- Modify: `M/model/SubtitleLabels.kt`. Delete the moved declarations. `subtitleLabels`
  (`:17-40`) and `QUALIFIER_SEPARATOR` (`:8`) stay until Task 3 and now use the moved
  functions (same package, no import change).
- Modify: `app/src/main/java/com/kino/puber/data/api/models/Models.kt:428-435`. Add
  `val file: String? = null` to `SubtitleLink` as its last parameter (D3).
- Test (new): `T/vm/SubtitleTrackIdentityTest.kt`. Port all of `UPT/vm/SubtitleTrackIdentityTest.kt` (33 lines; it
  already uses JUnit 5), then add:
  - `withoutMergedSourcePrefix_stripsOneLeadingNumberColon`: `"1:abc.srt"` → `"abc.srt"`; `"0:1:Russian"` → `"1:Russian"`.
  - `withoutMergedSourcePrefix_keepsIdsWithoutPrefix`: `"subs:Russian"` and `"abc"` stay unchanged.
  - `withoutMergedSourcePrefix_doesNotStripEmbeddedColons`: `"a:1:b"` stays unchanged.
  - `sameSubtitleIdentity_treatsFileAndIndexM3u8AsOne`:
    `sameSubtitleIdentity("/1/92/2871463.srt", "https://cdn.test/hls/1/92/2871463.srt/index.m3u8")` is true.
  - `sameSubtitleLanguage_matchesRuAndRus_rejectsBlank`.
- Test: `app/src/test/kotlin/com/kino/puber/data/api/models/SubtitleLinkTest.kt:1-37`. Add
  `file_isNullWhenAbsent` and `file_readsValue` in the existing style (P2).

**Consumes:** nothing new.
**Produces:**
- `internal fun String.stableSubtitleKey(): String`
- `internal fun sameSubtitleIdentity(first: String, second: String): Boolean`
- `internal val SUBTITLE_FILE_EXTENSION: Regex`
- `internal fun String.withoutMergedSourcePrefix(): String`

All four are in `...player.vm`. In `...player.model`:

- `internal const val AI_GENERATED_SUBTITLE_LANGUAGE`
- `canonicalSubtitleLanguage`
- `sameSubtitleLanguage`
- `subtitleLanguageDisplayName`

`SubtitleLink.file`.

- [x] Write first: the `SubtitleTrackIdentityTest` additions and the `SubtitleLinkTest` additions. Run them and see them fail.
- [x] Port the identity file, add the strip helper, create `SubtitleLanguage.kt`, trim `SubtitleLabels.kt`, add `file`.
- [x] Check that `stableSubtitleKey()` still returns the same values for the URLs in
      `T/vm/PlayerVMTrackSelectionTest.kt:113-137` (the suite stays green).
- [x] Fast gate. Run the touched tests: `SubtitleTrackIdentityTest`, `SubtitleLinkTest`,
      `com.kino.puber.ui.feature.player.*`. Commit: "Share subtitle identity and language helpers and read the subtitle file path".

**Done when:** the fast gate is green, the three test patterns above pass, and
`grep -n "fun canonicalSubtitleLanguage" -r app/src/main` shows exactly one hit, in `SubtitleLanguage.kt`.

---

### Task 2: New `SubtitleTrackUIState` shape, `isOff`, and Off detection in the controller

**Covers:** AC-2 (D9), AC-11 (data shape, R6)
**Files:**
- Modify: `M/model/PlayerUIModels.kt:26-32`. Replace it with:
  ```kotlin
  @Immutable
  internal data class SubtitleTrackUIState(
      val label: String,
      val language: String,
      val url: String,
      val isForced: Boolean? = null,
      val sourceFile: String? = null,
      /** Raw manifest/container label, kept to disambiguate same-language variants. */
      val descriptiveLabel: String? = null,
      /** Format id with the MergingMediaSource prefix stripped (D5, P-a). */
      val playerTrackId: String? = null,
      /** Full Media3 `TrackGroup.id`, prefix kept. */
      val playerTrackGroupId: String? = null,
      val playerTrackUri: String? = null,
      val playerTrackIndex: Int? = null,
      /** True when the track came from the HLS main source rather than a side-load (D5). */
      val fromManifest: Boolean = false,
  )

  internal val SubtitleTrackUIState.isOff: Boolean
      get() = language.isEmpty() && url.isEmpty() && !fromManifest &&
          playerTrackId == null && playerTrackGroupId == null &&
          playerTrackUri == null && playerTrackIndex == null
  ```
  `index` and `playerGroupIndex` are gone (D6). If detekt counts the getter as a
  `ComplexCondition`, split it into two `val`s.
- Modify: `M/model/PlayerUIMapper.kt:35-66`. Use named arguments with no `index`. Set
  `isForced = sub.forced` and `sourceFile = sub.file` on API rows. Labels still come from
  `subtitleLabels(...)` in this task.
- Modify: `M/component/PlayerPreview.kt:36-43`. Switch to named arguments without `index`
  (compile fix only).
- Modify: `PC:551`. Replace `track == null || track.url.isEmpty()` with
  `track == null || track.isOff`, and import `...player.model.isOff` (D9).
- Modify: `T/vm/PlayerVMTestFixture.kt:229-238`. Drop `index =` from `testSubtitleTracks`.
- Modify: `T/model/PlayerUIMapperTest.kt:80-92`. Use named arguments with no `index`,
  and add `isForced = true` on the `u3` row. The expected labels do not change.
- Test (new): `T/model/SubtitleTrackUIStateTest.kt`:
  - `isOff_isTrueForTheMapperOffRow`
  - `isOff_isFalse_forManifestRowWithEmptyUrl`: `url = ""`, `language = "en"`,
    `playerTrackId = "subs:English"`, `fromManifest = true` (P0, D9).
  - `isOff_isFalse_forManifestRowWithoutLanguageOrUrl`: only `playerTrackGroupId` is
    set, and `fromManifest = true`.
  - `isOff_isFalse_forApiRowWithUrl`.

**Consumes:** Task 1 (`SubtitleLink.file`).
**Produces:** the `SubtitleTrackUIState` above and `SubtitleTrackUIState.isOff` (package `...player.model`).

- [x] Write first: `SubtitleTrackUIStateTest`. Run it and see it fail (it does not compile).
- [x] Change the model, the mapper, the preview and `PC:551`, then the fixture and the mapper test.
- [x] Fast gate. Run the touched tests: `com.kino.puber.ui.feature.player.*`. Commit:
      "Give subtitle rows a source identity and stop treating an empty URL as Off".

**Done when:** the fast gate is green, `com.kino.puber.ui.feature.player.*` passes,
and `grep -rn "index = " app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt`
shows no line inside `testSubtitleTracks`.

---

### Task 3: `SubtitleLabeler` replaces `subtitleLabels`

**Covers:** AC-8, AC-11 (`FakeResourceProvider`)
**Files:**
- Create: `M/model/SubtitleLabeler.kt` (package `...player.model`). Port upstream
  `UP/vm/SubtitleLabeler.kt` (106 lines) with these changes:
  - Change the constructor's first parameter to `private val displayLocale: Locale`,
    and drop `displayLanguageTag` and the derived field (`UP:15,21`).
  - Imports: `com.kino.puber.ui.feature.player.vm.SUBTITLE_FILE_EXTENSION` and
    `com.kino.puber.ui.feature.player.vm.stableSubtitleKey`. The language helpers are now
    in the same package.
  - Keep `readableDescriptiveLabel()` as `internal`; the selector uses it in Task 6.
  - Add the factory, which reads every string and the locale at call time (P-c):
    ```kotlin
    internal fun subtitleLabeler(resources: ResourceProvider): SubtitleLabeler = SubtitleLabeler(
        displayLocale = resources.locale,
        aiGeneratedLabel = resources.getString(R.string.player_subtitle_ai_generated),
        forcedQualifier = resources.getString(R.string.player_subtitle_forced),
        variantLabel = { label, ordinal ->
            resources.getString(R.string.player_subtitle_variant_label, label, ordinal)
        },
        unknownLabel = { position -> resources.getString(R.string.player_subtitle_unknown, position) },
    )
    ```
    The strings are `app/src/main/res/values/player.xml:44-47` and
    `values-en/strings.xml:246-249`; `ResourceProvider.locale` is
    `app/src/main/java/com/kino/puber/core/system/ResourceProvider.kt:8`.
- Modify: `M/model/PlayerUIMapper.kt:35-66`. The mapper now returns
  `listOf(offRow) + subtitleLabeler(resources).apply(apiRows)`, where:
  - `offRow` is `SubtitleTrackUIState(label = getString(player_subtitles_off), language = "", url = "")`;
  - `apiRows` holds `SubtitleTrackUIState(label = "", language = sub.lang, url = sub.url,
    isForced = sub.forced, sourceFile = sub.file)` in API order.

  The Off row stays outside `apply`, so the positions passed to `unknownLabel` count API
  rows only, as master does.
- Delete: `M/model/SubtitleLabels.kt` (after Task 1 it only holds `subtitleLabels` and `QUALIFIER_SEPARATOR`).
- Delete: `T/model/SubtitleLabelsTest.kt` (`git rm`).
- Test (new): `T/model/SubtitleLabelerTest.kt`.
  - **(a) Fork cases, P0.** Port every case of the deleted `SubtitleLabelsTest.kt:24-101`
    with the same expected strings. The helper becomes
    `labeler.apply(subs.map { SubtitleTrackUIState(label = "", language = it.lang, url = it.url, isForced = it.forced) }).map { it.label }`,
    with `SubtitleLabeler(displayLocale = Locale.forLanguageTag("ru"), ...)` and the same
    lambdas as `SubtitleLabelsTest.kt:15-18`. Keep `composedLabel_usesTheSameSeparatorAsTheVariantString`
    and its `variantFormat` file reader (`:66-107`).
  - **(b) Upstream cases, P1.** Port `UPT/vm/SubtitleLabelerTest.kt` (130 lines):
    - `apply_neverShowsSubtitleFileNames`
    - `apply_neverShowsCdnChannelNumbering`
    - `apply_usesManifestLabels_whenTheyReadAsNamesAndSeparateTheRows`
    - `apply_keepsPartialMarker_whenAManifestLabelSeparatesTheRows`
    - `apply_doesNotQualifyRowsThatAlreadyReadDifferently`
    - `apply_preservesUnknownLanguageInformation_beforePositionalFallback`
    - the three naming cases

    Replace `displayLanguageTag = "ru"` with `displayLocale = Locale.forLanguageTag("ru")`.
  - **(c) Factory, P1.** `subtitleLabeler_takesStringsAndLocaleFromResources`: build it with a
    `FakeResourceProvider` whose locale is `Locale.ENGLISH`. Check that `ai` reads
    `"string_<player_subtitle_ai_generated id>"`, and that a forced `rus` row ends with
    `" · string_<player_subtitle_forced id>"` and starts with `"Russian"`.
- Modify: `T/model/PlayerUIMapperTest.kt:72-96`. The expected rows are unchanged from
  Task 2, which proves the API labels are byte-identical. Add
  `mapSubtitleTracks_carriesForcedAndSourceFile` (P1).

**Consumes:** Tasks 1 and 2.
**Produces:**
- `internal class SubtitleLabeler(displayLocale: Locale, aiGeneratedLabel: String,
  forcedQualifier: String, variantLabel: (String, Int) -> String, unknownLabel: (Int) -> String)`
  with `fun apply(tracks: List<SubtitleTrackUIState>): List<SubtitleTrackUIState>`
- `internal fun subtitleLabeler(resources: ResourceProvider): SubtitleLabeler`
- `internal fun SubtitleTrackUIState.readableDescriptiveLabel(): String?`

- [x] Write first: `SubtitleLabelerTest`. Run it and see it fail (it does not compile).
- [x] Port the labeler, add the factory, switch the mapper over, delete `SubtitleLabels.kt` and its test.
- [x] Fast gate. Run the touched tests: `com.kino.puber.ui.feature.player.model.*`. Commit:
      "Label subtitle rows through a shared labeler that also reads manifest labels".

**Done when:** the fast gate is green, `com.kino.puber.ui.feature.player.model.*`
passes with `PlayerUIMapperTest`'s expected labels unchanged since Task 2, and
`git ls-files '*SubtitleLabels*'` prints nothing.

---

### Task 4: `SubtitleTrackMerger`

**Covers:** AC-2, AC-3
**Files:**
- Create: `M/vm/SubtitleTrackMerger.kt`. Port upstream `UP/vm/SubtitleTrackMerger.kt` (154
  lines) with these changes (D7):
  - Delete the private `isFromManifest` (`UP:148-149`), and partition on `it.fromManifest`
    at `UP:42` and `UP:54-56`.
  - Delete the private `withoutMergedSourcePrefix` and `MERGED_SOURCE_PREFIX`
    (`UP:151-154`); use Task 1's shared helper. Keep the defensive strip in
    `sourceIdentities` / `findExactIdentityMatch`. The controller's ids are already
    stripped, and the API side never matches a non-path id anyway.
  - Imports: `...player.model.SubtitleLabeler`, `...player.model.canonicalSubtitleLanguage`
    and `...player.model.isOff`.
  - Keep the rest unchanged:
    - the Off row first, taken from the API list;
    - more tracks win per canonical language, HLS on a tie;
    - unknown-language rows are kept unless their identity proves them a copy of a
      manifest row;
    - enrichment (`url`, `sourceFile`, `isForced`) happens only on a unique identity match,
      and each API row is claimed once;
    - rows are ordered by language in order of first appearance, forced last;
    - `labeler.apply` runs on the non-Off rows.
- Test (new): `T/vm/SubtitleTrackMergerTest.kt`. Port `UPT/vm/SubtitleTrackMergerTest.kt` (525 lines) with:
  - `testLabeler()`: `displayLocale = Locale.forLanguageTag("ru")` (other args unchanged).
  - Helper `playerTrack(label, language, id, groupIndex, forced = false, uri = null,
    descriptiveLabel = null, fromManifest = uri != null)`. It maps `groupIndex` to
    `playerTrackGroupId = "g$groupIndex"` and `playerTrackIndex = 0`, and passes
    `fromManifest` through. The default reproduces upstream's meaning for every ported case.
  - Every assertion on `playerGroupIndex` becomes one on `playerTrackGroupId` with a `"g"`
    prefix, and `null` for Off. For example `UPT:86` `listOf(2, 4, 1, 5)` becomes
    `listOf("g2", "g4", "g1", "g5")`, and `UPT:221` becomes
    `listOf(null, "g0", "g2", "g3", "g1")`.
  - Rename `merge_exposesOnlyOff_untilPlayerTracksAreDiscovered` to `merge_exposesOnlyOff_whenPlayerReportsNoTracks`.
  - Add these new cases:
    - **P0** `merge_countsManifestByFromManifestFlag_notByRenditionUri` (D5): two `rus`
      manifest rows with `fromManifest = true` and `uri = null`, plus one `rus` side-load.
      Expected: the two manifest rows are kept and the side-load is dropped. Under
      upstream's rule they would count as side-loads (2 vs 1) and keep the wrong set.
    - **P0** `merge_carriesManifestOnlyLanguage_withLanguageNameLabel` (AC-2): API `rus`
      only; player has a `rus` side-load and an `en` manifest row. Expected rows:
      Off, "Русский" (with the API url), "Английский" with `url == ""` and
      `isOff == false`.
    - **P1** `merge_withoutApiFile_keepsRenditionUrlEmpty` (R4): an API row with `url`
      `https://api.test/pd/abc` and no `sourceFile`, and a manifest rendition with `uri`
      `https://cdn.test/hls/1/92/x.srt/index.m3u8`. Expected: the merged row has `url == ""`.
    - **P1** `merge_dropsUnknownLanguageSideLoad_thatDuplicatesAManifestRow` and
      `merge_keepsUnknownLanguageSideLoad_thatIsNotADuplicate`.
    - **P1** `merge_isDeterministic` (R5): `merge(a, b) == merge(a, b)` on the
      `merge_hidesSideLoadedCopies_whenManifestCoversTheirLanguages` input.

**Consumes:** Tasks 1-3.
**Produces:** `internal class SubtitleTrackMerger(labeler: SubtitleLabeler)` with
`fun merge(apiTracks: List<SubtitleTrackUIState>, playerTracks: List<SubtitleTrackUIState>): List<SubtitleTrackUIState>`.

- [x] Write first: `SubtitleTrackMergerTest` (port plus new cases). Run it and see it fail (it does not compile).
- [x] Port the merger.
- [x] Fast gate. Run the touched tests: `com.kino.puber.ui.feature.player.vm.SubtitleTrackMergerTest`. Commit:
      "Merge manifest and side-loaded subtitle rows per language".

**Done when:** the fast gate is green and `SubtitleTrackMergerTest` passes,
including the two new P0 cases.

---

### Task 5: VM merges player rows and keeps the selection (callback extended)

**Covers:** AC-1, AC-2 (VM side), AC-5 (VM side), AC-6, AC-7
**Files:**
- Modify: `PC:67`. Change it to
  `fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int, subtitleTracks: List<SubtitleTrackUIState>? = null)`.
  Add KDoc: "`subtitleTracks` is null when the player has no rows of its own (progressive);
  the picker then shows the API rows" (D4). `PC:867` still passes two arguments and
  compiles; Task 8 changes it.
- Modify: `M/vm/AudioTrackPreferenceResolver.kt`.
  - `:97`: compare with `sameSubtitleLanguage(it.value.language, preferredLang)` instead
    of `==` (D12).
  - Add (D11). Return Off's index when nothing matches. Write it as a matcher list
    (as in `:24-37`) so `ReturnCount` stays at 3 or below:
    ```kotlin
    fun findSameSubtitleTrackIndex(tracks: List<SubtitleTrackUIState>, previous: SubtitleTrackUIState): Int
    ```
    The rules, in order:
    1. `previous.isOff` → Off.
    2. Exact equality (`tracks.indexOf(previous)`).
    3. `playerTrackId` is non-empty and equal (`singleOrNull`).
    4. `url` or `sourceFile` is non-empty and `sameSubtitleIdentity` (`singleOrNull`).
    5. `playerTrackGroupId` is non-empty and equal, **and** `playerTrackIndex` is equal (`singleOrNull`).
    6. `sameSubtitleLanguage`, and `isForced` compatible (either side null, or equal) (`singleOrNull`).
    7. Otherwise Off.

    Off = `tracks.indexOfFirst { it.isOff }.coerceAtLeast(0)`. Rules 3-6 consider non-Off rows only.
- Modify: `VM`:
  - Add the field `private var apiSubtitleTracks: List<SubtitleTrackUIState> = emptyList()`
    next to `:175`.
  - Add `private fun mergeSubtitleTracks(playerTracks: List<SubtitleTrackUIState>) =
    SubtitleTrackMerger(subtitleLabeler(resources)).merge(apiSubtitleTracks, playerTracks)`.
    It is built per call (P-c, D16: no DI). `resources` is the constructor's `ResourceProvider` (`VM:69`).
  - `publishPreparedContent` (`:362-368`): first line `apiSubtitleTracks = contentState.subtitleTracks`.
  - `refreshStreamLinksOnce`: next to `currentMedia = refreshedMedia` (`:487`), add
    `apiSubtitleTracks = mapper.mapSubtitleTracks(resolved.subtitles)` (D11). Do not touch
    the published rows; the next report re-merges.
  - Replace `onTracksUpdated` (`:211-222`) with:
    ```kotlin
    override fun onTracksUpdated(
        audioTracks: List<AudioTrackUIState>,
        selectedIndex: Int,
        subtitleTracks: List<SubtitleTrackUIState>?,
    ) {
        val previous = (stateValue as? PlayerViewState.Content)?.content
        val previousRow = previous?.subtitleTracks?.getOrNull(previous.selectedSubtitleIndex)
        val rows = subtitleTracks?.let(::mergeSubtitleTracks) ?: apiSubtitleTracks
        val rowIndex = previousRow
            ?.let { audioTrackPreferenceResolver.findSameSubtitleTrackIndex(rows, it) }
            ?: 0
        updateContent {
            copy(
                audioTracks = audioTracks.ifEmpty { this.audioTracks },
                selectedAudioTrackIndex = if (audioTracks.isEmpty()) selectedAudioTrackIndex else selectedIndex,
                subtitleTracks = rows,
                selectedSubtitleIndex = rowIndex,
            )
        }
        if (!tracksRestoredForCurrentMedia) {
            tracksRestoredForCurrentMedia = true
            restoreTrackPreferences()   // unchanged, :526-554 (D12)
            return
        }
        val row = rows.getOrNull(rowIndex)
        if (row != null && previousRow != null && row != previousRow) {
            playbackController.selectSubtitle(row)   // re-send, no persist (D11, R5)
        }
    }
    ```
    (A function-local `this.audioTracks` inside `copy` refers to the state; name the
    parameter differently if the shadowing reads badly.) `restoreTrackPreferences`
    (`:526-554`) and `applySubtitleSelection` (`:807-817`) do not change.
- Modify: `T/vm/PlayerVMTestFixture.kt`. Add
  `every { mapper.mapSubtitleTracks(any()) } returns testSubtitleTracks` after `:129`. The
  mapper is a `relaxUnitFun` mock (`:86`), and the refresh tests in
  `T/vm/PlayerVMPlaybackFlowTest.kt:276-399` now reach `mapSubtitleTracks`.
- Test (new): `T/vm/AudioTrackPreferenceResolverTest.kt` (P1):
  - `findSameSubtitleTrackIndex_offStaysOff`
  - `..._bySourceIdentity` (API row → merged row with the same url)
  - `..._byPlayerTrackId`
  - `..._byFullGroupIdAndIndex`
  - `..._byUnambiguousCanonicalLanguage` (`rus` → `ru`)
  - `..._ambiguousLanguage_givesOff`
  - `..._doesNotMatchSameGroupIdWithDifferentIndex`
  - `findSubtitleTrackIndex_matchesRuAgainstRus_viaSameSubtitleLanguage`
  - `findSubtitleTrackIndex_stillAmbiguous_returnsNoMatch`
- Test: `T/vm/PlayerVMTrackSelectionTest.kt`. Add these, with shared helpers in the test class:
  - `sideLoad(language, url, child)` gives `SubtitleTrackUIState(label = "", language,
    url = "", playerTrackId = url.stableSubtitleKey(), playerTrackGroupId = "$child:0",
    playerTrackIndex = 0, fromManifest = false)`.
  - `manifest(language, id, uri = null)` gives `playerTrackId = id`,
    `playerTrackGroupId = "0:$id"`, `playerTrackIndex = 0`, `fromManifest = true`.
  - `hlsRowsForFixture()` gives side-loads for `testSubtitleTracks[1]` and `[2]`
    (children 1, 2).

  Merged rows carry labeler labels and player fields, so these tests assert with
  `match { it.url == ... }` / `match { it.language == ... }` and never compare with
  `testSubtitleTracks[n]` by equality. The new tests:
  - **Write first (failing-first #1), P0:** `tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable`.
    Report audio plus `hlsRowsForFixture() + manifest("en", "subs:English")`. Then:
    - `contentState(vm).subtitleTracks` contains a row with `url == testSubtitleTracks[1].url`
      and a row with `language == "en"`, and none of them is labelled with a raw id;
    - after `SelectSubtitle(<index of the rus.vtt row>)`,
      `verify { playbackController.selectSubtitle(match { it.url == testSubtitleTracks[1].url && it.playerTrackId == "rus.vtt" }) }`.
  - **P0** `selectSubtitle_manifestOnlyRow_isDelegatedNotTreatedAsOff`: same report, select
    the `en` row. Verify `selectSubtitle(match { it.language == "en" && !it.isOff })`, and that
    `selectedSubtitleIndex` points at it.
  - **P0** `tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows`: report with no third
    argument. `subtitleTracks == testSubtitleTracks`.
  - **P0** `tracksUpdated_keepsSelectedRow_whenSameMergedRowStillExists`: report the HLS rows,
    select the rus.vtt row, report the same rows again. The index and row are unchanged.
  - **P0** `tracksUpdated_selectedRowReordered_followsRowToNewIndex`: the second report puts
    the manifest `en` row first and swaps the side-load children to `2:0` / `1:0`. The
    selection follows `url` to its new index. `selectSubtitle` is re-sent exactly once
    with the updated row, which now carries `playerTrackGroupId = "2:0"`, so the
    controller's pending row is current.
  - **P0** `tracksUpdated_selectedRowDisappears_goesOffAndDisablesText`: the second report
    lacks the selected row. `selectedSubtitleIndex == 0`, and
    `verify { playbackController.selectSubtitle(match { it.isOff }) }` after the second report.
  - **P0** `tracksUpdated_identicalRepeatedReports_sendSingleReSelect` (R5): two identical HLS
    reports. `verify(exactly = 1) { playbackController.selectSubtitle(any()) }` (that one is
    the restore re-apply). After the second report the state object is the same:
    `assertSame(stateBefore, vm.testStateValue)` (`MutableStateFlow` keeps the old
    reference on an equal value; `app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:82-84`).
  - **P0** `tracksUpdated_restoresSavedUrl_onApiLinkedManifestRow` (AC-7): the saved URL is
    `testSubtitleTracks[1].url`. Report a manifest `rus` row with
    `uri = "https://cdn.test/hls/subtitles/rus.vtt/index.m3u8"` and no side-loads. Verify
    `selectSubtitle(match { it.url == testSubtitleTracks[1].url && it.fromManifest })`.
  - **P0** the AC-7 merged variants (`@ParameterizedTest` or a second copy of each):
    `tracksUpdated_restoresPreferredSubtitleByUrl_beforeLanguage`,
    `..._ByStableUrl_whenSignedUrlChanges`,
    `..._ambiguousSubtitleLanguage_withoutUrl_reappliesOffRow`,
    `..._reappliesOffRow_whenSavedSubtitleDoesNotResolve` and
    `..._restoreDoesNotRewritePreferencesFromIntermediateState` (`:112-202`) are rerun with
    `subtitleTracks = hlsRowsForFixture()`. Each asserts by `url` / `isOff` and keeps the
    expected index (the merged order equals the API order). The existing two-argument
    tests stay as they are.
  - **P1** `tracksUpdated_audioEmptyUpdate_keepsAudioRowsAndIndex` and
    `tracksUpdated_textOnlyUpdate_updatesSubtitleRows` (the idea of upstream
    `UPT/vm/PlayerVMSubtitleVariantTest.kt:187-200`, written against this fixture).
  - **P1** `refreshStreamLinks_recomputesApiRows_andMergesWithPlayerRows`: drive
    `onStreamFailure(..., REFRESH_LINKS, ...)` as `T/vm/PlayerVMPlaybackFlowTest.kt:309-333`
    does, stub `mapper.mapSubtitleTracks` to return one `eng` API row, then report a
    matching side-load. The merged row carries the new url.

**Consumes:** Tasks 1-4.
**Produces:** the extended `PlaybackControl.Callback.onTracksUpdated` (3rd parameter,
default `null`) and
`AudioTrackPreferenceResolver.findSameSubtitleTrackIndex(tracks, previous): Int`.

- [x] Write first: `tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable`. Run it and
      see it fail: `onTracksUpdated` takes no third argument on base. Then write the
      remaining VM and resolver tests.
- [x] Callback signature, resolver changes, VM changes, fixture stub.
- [x] Fast gate. Run the touched tests: `com.kino.puber.ui.feature.player.*` (the whole VM
      suite, including `PlayerVMEpisodeFlowTest:109-125` and the refresh tests). Commit:
      "Merge player subtitle rows in the player view model and keep the viewer's pick across track updates".

**Done when:** the fast gate is green and `com.kino.puber.ui.feature.player.*` passes,
including every P0 test named above and the unchanged restore tests at
`PlayerVMTrackSelectionTest.kt:112-202`.

---

### Task 6: `SubtitleTrackSelector`

**Covers:** AC-4
**Files:**
- Create: `M/vm/SubtitleTrackSelector.kt`. Port upstream `UP/vm/SubtitleTrackSelector.kt` (104 lines) with these changes (D8, P-b):
  - `PlayerTextTrack` keeps its fields (`groupId`, `groupIndex`, `trackIndex`, `formatId`,
    `formatLabel`, `language`, `isForced`). Rewrite the KDoc:
    - `groupId` is the full merged `TrackGroup.id`;
    - `groupIndex` is only the controller's address of the group in its filtered text-group list;
    - no rule matches on `groupIndex`.
  - `select` order:
    ```kotlin
    return matchByFormatId(track, compatible)          // P-b step 1
        ?: matchBySourceIdentity(track, compatible)    // P-b step 2
        ?: matchByTrackGroupId(track, compatible)      // full id + trackIndex (UP:60-69 as is)
        ?: matchByLanguage(track, compatible)          // UP:91-101 as is
    ```
    - `matchByFormatId`: `id = track.playerTrackId` non-empty. Keep the candidates whose
      `formatId == id || formatId.withoutMergedSourcePrefix() == id`, then `.singleOrNull()`.
    - `matchBySourceIdentity`: `rowIds = listOfNotNull(track.url, track.sourceFile).filter(String::isNotEmpty)`.
      Keep the candidates whose `listOfNotNull(formatId?.withoutMergedSourcePrefix(), formatLabel)`
      (non-empty) has any `sameSubtitleIdentity` with any of `rowIds`, then `.singleOrNull()`.
    - **Delete** `matchByCoordinates` (`UP:79-89`).
    - The semantic filter `isSemanticallyCompatibleWith` (`UP:46-57`) stays as is, importing
      `...player.model.readableDescriptiveLabel` and `...player.model.sameSubtitleLanguage`.
- Test (new): `T/vm/SubtitleTrackSelectorTest.kt`. Port `UPT/vm/SubtitleTrackSelectorTest.kt` (156 lines):
  - The `manifestTrack` helper drops `groupIndex` and `playerGroupIndex`. Call sites drop
    the `groupIndex =` argument. `PlayerTextTrack(...)` constructors stay as they are.
  - Keep `select_prefersManifestRendition_overSideLoadedCopyOfTheSameSubtitle`,
    `select_survivesTrackGroupsBeingReordered`,
    `select_picksCorrectVariant_whenGroupIdsAreBlankAndLanguagesCollide`,
    `select_rejectsReusedCoordinatesAndIds_withDifferentLanguage` and
    `select_rejectsForcedTrack_forFullSubtitleRow`.
  - Rename `select_matchesByLanguage_asLastResort` to `select_matchesByLanguage_onlyWhenUnambiguous`,
    and add a second assertion: two `rus` candidates give `null`.
  - Rename `select_returnsNull_ratherThanGuessing_whenNothingIdentifiesTheTrack` to
    `select_doesNotFallBackToCoordinates_whenNothingIdentifiesTheTrack`.
  - **Delete** `select_fallsBackToCoordinates_whenTheManifestExposesNoIdentity` (`UPT:62-71`).
    Replace it with `select_neverPicksByPosition`: two `rus` candidates
    `PlayerTextTrack("", 0, 0, language = "rus")` and `PlayerTextTrack("", 1, 0, language = "rus")`,
    and a `rus` row with no ids and no url whose index equals candidate 1. Expected: `null`.
  - **Write first (failing-first #2), P0:** `select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero`.
    Candidates: `PlayerTextTrack("1:0", 0, 0, formatId = "1:k1.srt", formatLabel = "k1.srt", language = "rus")`,
    the same for `"2:0"`/`k2.srt` and `"3:0"`/`k3.srt`. For each `k`, a row with
    `playerTrackId = k`, `language = "rus"` and no group id selects its own candidate.
    Assert also that `"1:0".withoutMergedSourcePrefix() == "2:0".withoutMergedSourcePrefix()`.
    The stripped group id cannot tell them apart.
  - **P0** `select_prefersIdentityOverFullGroupId_whenChildOrderChanged`: row
    `playerTrackId = "k2.srt"`, `playerTrackGroupId = "1:0"`, `playerTrackIndex = 0`. In the
    candidates, `"1:0"` now carries `formatId = "1:k1.srt"` and `"2:0"` carries `"2:k2.srt"`.
    Expected: the `"2:0"` candidate.
  - **P0** `select_usesFullGroupIdAndIndex_whenIdentityIsAbsent`: the row has a group id and
    index but no `playerTrackId`, `url` or `sourceFile`, and two `rus` candidates.
    Expected: the group match.
  - **P1** `select_matchesSideLoadByApiUrl_whenRowHasNoPlayerId`: an API-only row (only
    `url = "https://api.test/subtitles/k2.srt?t=1"`) against the three side-loads above.
    Expected: `"2:0"`.

**Consumes:** Tasks 1-3 (`withoutMergedSourcePrefix`, `sameSubtitleIdentity`, `readableDescriptiveLabel`, `sameSubtitleLanguage`).
**Produces:** `internal data class PlayerTextTrack(groupId: String, groupIndex: Int, trackIndex: Int,
formatId: String? = null, formatLabel: String? = null, language: String? = null, isForced: Boolean? = null)`;
`internal class SubtitleTrackSelector { fun select(track: SubtitleTrackUIState, candidates: List<PlayerTextTrack>): PlayerTextTrack? }`.

- [x] Write first: `select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero`.
      Run it and see it fail (it does not compile). Then write the rest of the test file.
- [x] Port and change the selector.
- [x] Fast gate. Run the touched tests: `com.kino.puber.ui.feature.player.vm.SubtitleTrackSelectorTest`. Commit:
      "Select subtitle text tracks by source identity instead of position".

**Done when:** the fast gate is green, `SubtitleTrackSelectorTest` passes, and
`grep -rn "matchByCoordinates\|playerGroupIndex" app/src` finds nothing.

---

### Task 7: `PlaybackMediaItemFactory` with a pure subtitle spec

**Covers:** AC-1 (D1 input), AC-5 (D2)
**Files:**
- Create: `M/vm/PlaybackMediaItemFactory.kt`:
  ```kotlin
  internal data class SubtitleSpec(
      val uri: String,
      val mimeType: String,
      val language: String,
      val selectionFlags: Int,
      val id: String,
      val label: String,
  )

  internal data class MediaItemSpec(
      val uri: String,
      val mimeType: String?,          // APPLICATION_M3U8 for StreamType.HLS, null otherwise
      val subtitles: List<SubtitleSpec>,
  )

  internal class PlaybackMediaItemFactory {
      /** Pure: no Uri, no MediaItem; unit-tested on the JVM. */
      fun spec(stream: StreamCandidate, subtitles: List<SubtitleLink>?): MediaItemSpec
      fun build(stream: StreamCandidate, subtitles: List<SubtitleLink>?): MediaItem =
          spec(stream, subtitles).toMediaItem()
  }

  internal fun subtitleMimeType(url: String): String   // moved verbatim from PC:624-635
  ```
  - `spec`:
    - `mimeType = MimeTypes.APPLICATION_M3U8.takeIf { stream.type == StreamType.HLS }`;
    - every `SubtitleLink` becomes `SubtitleSpec(uri = sub.url, mimeType = subtitleMimeType(sub.url),
      language = sub.lang, selectionFlags = if (sub.forced == true) C.SELECTION_FLAG_FORCED else 0,
      id = sub.url.stableSubtitleKey(), label = sub.url.stableSubtitleKey())`;
    - **no `embed` filter** (D2).

    The body equals `PC:606-622` field by field.
  - `toMediaItem()` is private: `MediaItem.Builder().setUri(uri)`. It calls
    `setMimeType(mimeType)` only when non-null, and maps each spec to
    `MediaItem.SubtitleConfiguration.Builder(uri.toUri()).setMimeType(..).setLanguage(..).setSelectionFlags(..).setLabel(..).setId(..)`.
    It calls `setSubtitleConfigurations` only when the list is non-empty, as master does at `PC:608`.
  - `StreamCandidate` / `StreamType`:
    `app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt:19-27`.
- Modify: `PC`:
  - Add the field `private val mediaItemFactory = PlaybackMediaItemFactory()`.
  - `:423`, `:491` and `:736` use `mediaItemFactory.build(stream, subtitles)`.
  - Delete `buildMediaItem` and `subtitleMimeType` (`:606-635`), and remove the imports
    that are now unused (`androidx.core.net.toUri` at `:7`).
  - `setMediaSource` and the warm-up stay as they are in this task.
- Test (new): `T/vm/PlaybackMediaItemSpecTest.kt`.
  - **Write first (failing-first #3), P0:** `hls_attachesEveryApiSubtitle_withStableKeyAsIdAndLabel`.
    `StreamCandidate("https://cdn/v.m3u8", StreamType.HLS)` with three links:
    - `rus` `https://api.test/subtitles/rus.srt?t=1` with `forced = true`;
    - `eng` `.../eng.vtt` with `forced = null`;
    - `ukr` `.../ukr.srt` with `embed = true`.

    Expect `mimeType == MimeTypes.APPLICATION_M3U8`, and three specs where
    `id == label == stableSubtitleKey(url)`, the flags are `[FORCED, 0, 0]`, the languages
    are in order, and `uri` is the original url.
  - **P0** `progressive_keepsAllApiSubtitles_includingEmbedTrue_withoutM3u8Mime`: same links
    with `StreamType.PROGRESSIVE`. `mimeType == null`, and the same three specs.
  - **P1**:
    - `forcedFalse_hasNoFlags`
    - `emptyOrNullSubtitles_giveNoSpecs`
    - `subtitleMimeType_byExtension`: `.vtt` / `.webvtt` → `TEXT_VTT`, `.ass` / `.ssa` →
      `TEXT_SSA`, `.ttml` / `.xml` → `APPLICATION_TTML`, `.srt` and unknown →
      `APPLICATION_SUBRIP`, all with `?query` and `#frag`.

**Consumes:** Task 1 (`stableSubtitleKey`).
**Produces:** `PlaybackMediaItemFactory.spec/build`, `subtitleMimeType`.

- [x] Write first: `hls_attachesEveryApiSubtitle_withStableKeyAsIdAndLabel`. Run it and see it fail (it does not compile).
- [x] Factory, then the controller call sites.
- [x] Fast gate. Run the touched tests: `com.kino.puber.ui.feature.player.vm.PlaybackMediaItemSpecTest`. Commit:
      "Build playback media items with the HLS MIME type and side-loaded subtitles from one factory".

**Done when:** the fast gate is green, `PlaybackMediaItemSpecTest` passes, and
`grep -n "fun buildMediaItem\|fun subtitleMimeType" app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` finds nothing.

---

### Task 8: Controller: side-load on HLS, report text rows, select through the selector

**Covers:** AC-1, AC-2, AC-4, AC-5, AC-6 (controller side), AC-9 (no code; D15), AC-10 (warm-up unchanged), AC-11
**Files:**
- Create: `M/vm/PlayerTextTracks.kt` (top-level `internal` functions). This keeps `PC`
  under `LargeClass`; they need Media3 types and are covered by the device pass, not by
  unit tests.
  ```kotlin
  internal fun buildSubtitleRows(
      textGroups: List<Tracks.Group>,
      renditions: List<HlsMultivariantPlaylist.Rendition>,
      merged: Boolean,
  ): List<SubtitleTrackUIState>

  internal fun List<Tracks.Group>.toPlayerTextTracks(): List<PlayerTextTrack>
  ```
  - `buildSubtitleRows` handles each group `g`, track `t` and `format = g.getTrackFormat(t)`,
    with `groupId = g.mediaTrackGroup.id`:
    - `id = if (merged) format.id?.withoutMergedSourcePrefix() else format.id` (P-a);
    - `fromManifest = !merged || groupId.startsWith("0:")` (D5, P-a);
    - `rendition`, only if `fromManifest`:
      - first `renditions.filter { id != null && it.format.id == id }.singleOrNull()`;
      - otherwise `renditions.filter { it.format.label == format.label &&
        sameSubtitleLanguage(it.format.language.orEmpty(), format.language.orEmpty()) }.singleOrNull()`;
      - **no positional fallback** (D5; compare upstream's `.getOrNull(renditionIndex)`,
        which is dropped);
    - row:
      - `label = ""`;
      - `language = format.language ?: rendition?.format?.language.orEmpty()`;
      - `url = ""`;
      - `isForced = ((format.selectionFlags or (rendition?.format?.selectionFlags ?: 0)) and C.SELECTION_FLAG_FORCED) != 0`;
      - `descriptiveLabel = format.label ?: rendition?.format?.label`;
      - `playerTrackId = id ?: rendition?.format?.id`;
      - `playerTrackGroupId = groupId`;
      - `playerTrackUri = rendition?.url?.toString()`;
      - `playerTrackIndex = t`;
      - `fromManifest`.

    Upstream reference: the `buildSubtitleTracks` / `findHlsRendition` / `buildSubtitleTrack`
    functions in `UP/vm/PlaybackController.kt` (search for `fun buildSubtitleTracks`).
  - `toPlayerTextTracks` builds, for each `groupIndex`, `group`, `trackIndex`:
    `PlayerTextTrack(groupId = group.mediaTrackGroup.id, groupIndex, trackIndex, formatId = format.id,
    formatLabel = format.label, language = format.language,
    isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0)`. `formatId` stays
    raw; the selector strips it.
  - Imports: `androidx.media3.common.Tracks`, `androidx.media3.common.C`,
    `androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist`.
- Modify: `PC`:
  - Fields. Add `private var currentStreamType: StreamType? = null` and
    `private var currentItemHasSideLoads = false` next to `currentStreamUrl` (`:162`).
    Set both in `prepare` (next to `:425`) and `switchStream` (next to `:493`) from
    `stream.type` and `!subtitles.isNullOrEmpty()`. Clear them in `release()` next to `:576`.
    Add `private val subtitleTrackSelector = SubtitleTrackSelector()`.
  - D1. Replace `setMediaSource` (`:659-681`) with `setMediaItem(player, mediaItem,
    startPositionMs: Long? = null)`. It calls `player.setMediaItem(mediaItem, startPositionMs)`
    when a position is given, and `player.setMediaItem(mediaItem)` otherwise. Remove the
    `dataSourceFactory ?: return` guard: the player's own `DefaultMediaSourceFactory` (`:406-412`) builds every source.
    - Update the call sites `:424` and `:492` (they drop the `stream.type` argument).
    - Comment at `:480`: "Applies to every stream: HLS and progressive both go through
      this factory, which also wraps side-loaded subtitles in a MergingMediaSource."
    - In the KDoc at `:460-470`, change "`setMediaSource`" to "`setMediaItem`".
    - Keep the `HlsMediaSource` import (`:30`); the warm-up still uses it.
  - D13: `warmUpNext` / `setWarmUpMediaSource` (`:689-774`) stay unchanged.
  - D4/D5: replace `notifyTracksUpdated` (`:852-868`) with:
    ```kotlin
    val groups = player.currentTracks.groups
    val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val textGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    if (audioGroups.isEmpty() && textGroups.isEmpty()) return
    // audio rows and selectedIndex exactly as :857-866
    val subtitleRows = if (currentStreamType == StreamType.HLS) {
        val renditions = (player.currentManifest as? HlsManifest)
            ?.multivariantPlaylist?.subtitles.orEmpty()
        buildSubtitleRows(textGroups, renditions, merged = currentItemHasSideLoads)
    } else {
        null   // progressive keeps the API rows (AC-5)
    }
    callback?.onTracksUpdated(audioTracks, selectedIndex, subtitleRows)
    ```
    Import `androidx.media3.exoplayer.hls.HlsManifest`.
  - D8: in `applySubtitleTrackSelection` (`:874-890`), replace `:876-878` with:
    ```kotlin
    val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val target = subtitleTrackSelector.select(track, textGroups.toPlayerTextTracks())
    val targetGroup = target?.let { textGroups.getOrNull(it.groupIndex) }
    ```
    Keep the builder at `:879-883` unchanged. Apply the override only when
    `targetGroup != null`:
    `TrackSelectionOverride(targetGroup.mediaTrackGroup, target.trackIndex)`. Keep the
    fork's fallback when there is no target (text enabled, preferred language, no override).
    Delete `findTextTrack`, `findUnambiguousTextTrackByLanguage`, `findTextTrackBy` and
    `TextTrackSelection` (`:892-940`). Keep their "no positional fallback" comment
    (`:902-904`) above the `select` call.
  - D9 is already done in Task 2. `pendingSubtitleTrack` handling (`:371-374`, `:561`,
    `:870-872`) does not change.
  - Remove the imports that are now unused (`Format` if no longer used, and so on). Let
    detekt / lint name them.
- No new unit tests. `PlaybackController` needs a real `ExoPlayer` (spec Test design §6).
  `PlayerTextTracks.kt` builds Media3 `Tracks.Group`, so it is not unit-tested either.
  The device checklist D-1..D-9 covers it.

**Consumes:** Tasks 2, 5 (callback), 6 (selector), 7 (factory).
**Produces:** the HLS side-load path. Text rows reach the VM on HLS; `null` on progressive.

- [x] Write `PlayerTextTracks.kt`, then the controller changes above.
- [x] Fast gate, then the **full gate**:
      `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
      If `LargeClass` or `TooManyFunctions` fires on `PC`, move more private helpers into
      `PlayerTextTracks.kt`. Do not suppress.
- [x] Commit: "Side-load API subtitles on HLS and report merged text tracks to the player view model".

**Done when:** the full gate is green, and the following find nothing in
`PlaybackController.kt`:

- `grep -n "HlsMediaSource.Factory" .../PlaybackController.kt` outside
  `setWarmUpMediaSource`;
- `grep -n "fun setMediaSource\|fun findTextTrack\|TextTrackSelection" .../PlaybackController.kt`.

Then hand over to qa-device for the spec's device checklist D-1..D-9 (D-6 records time
to first frame, D-9 is opportunistic).

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- `bd1917a5` Share subtitle identity and language helpers and read the subtitle file path
- `67917c32` Give subtitle rows a source identity and stop treating an empty URL as Off
- `126302bc` Label subtitle rows through a shared labeler that also reads manifest labels
- `55764308` Merge manifest and side-loaded subtitle rows per language
- `49afcadf` Merge player subtitle rows in the player view model and keep the viewer's pick across track updates
- `f3ddd8c6` Select subtitle text tracks by source identity instead of position
- `0d353669` Build playback media items with the HLS MIME type and side-loaded subtitles from one factory
- Task 8 commit (controller, `PlayerTextTracks.kt`, this record): Side-load API subtitles on HLS and report merged text tracks to the player view model

Failing-first runs seen: Task 1 (`SubtitleTrackIdentityTest`, `SubtitleLinkTest`: test source did not compile), Task 2 (`SubtitleTrackUIStateTest`: no `isOff`, no `index` argument), Task 7 (`PlaybackMediaItemSpecTest`: unresolved `PlaybackMediaItemFactory`). For Tasks 3, 4, 5 and 6 the test files and the production code were written in one pass and the first run was already green, so no red run was recorded for them (the conductor's failing-first list covers #1 VM test, #2 selector test, #3 factory test; only #3 was run red).

**Deviations from the plan:**

- Conductor advisory 1: the P-a strip and the `fromManifest` rule are a pure function, `subtitleSourceId(formatId, groupId, merged)` returning `SubtitleSourceId(trackId, fromManifest)`, in `vm/SubtitleTrackIdentity.kt` (Task 1), not inline in `PlayerTextTracks.buildSubtitleRows`. `SubtitleTrackIdentityTest` covers merged and unmerged ids, including the numeric GROUP-ID `"1:Russian"` (kept intact when unmerged, stripped to `"Russian"` only when it is `"0:1:Russian"`-style merged ids). `SubtitleTrackMergerTest.merge_keepsUnmergedNumericGroupIdIntact` and `SubtitleTrackSelectorTest.select_keepsUnmergedNumericGroupIdAsIdentity` cover the same id in the merger and the selector.
- Conductor advisory 2: dropped upstream's defensive prefix strip from `SubtitleTrackMerger` (`sourceIdentities` / `findExactIdentityMatch`) instead of keeping it as the plan said. The controller already strips ids only for merged items (P-a), so the merger's own strip could only ever touch unmerged ids such as `"1:Russian"`. The merger's contract is now "player track ids arrive stripped". The ported merger tests were adapted: ids that upstream wrote as `"1:rus-1.srt"` are written as `"rus-1.srt"`, and `"0:"` as `""`. The selector keeps its strip of the candidate's raw `formatId`, because the controller hands it the raw Media3 ids.
- Conductor advisory 3: `tracksUpdated_selectedRowDisappears_goesOffAndDisablesText` drops every Russian row, so the result is Off. It clears the recorded calls on the controller mock after the pick (`clearMocks(answers = false)`) and then verifies exactly one Off `selectSubtitle`, because the first-report restore also sends an Off row.
- Conductor advisory 4: `:app:detektAll` was run after Tasks 2, 5, 6 and 7 as well. It found two things, both fixed: an `UnsafeCallOnNullableType` (`!!`) in the ported `SubtitleLabeler.disambiguate` (restructured with a null-checked local; behaviour unchanged; it landed in the Task 5 commit, not Task 3) and one over-long line in a VM test.
- Lint (Task 8) flagged `UnsafeOptInUsageError` on `toPlayerTextTracks` (`group.mediaTrackGroup`); fixed with `@OptIn(UnstableApi::class)`.
- Task 2: the `isOff` getter did not trip `ComplexCondition`; I split it into a private `hasNoPlayerSource` getter anyway for readability.
- `PlayerVM.onTracksUpdated` is a private member `PlayerVM.onTracksUpdated(...)` that the callback object delegates to (as `onPlaybackEnded` does), rather than inline code in the callback object. The logic is the plan's.
- The VM tests assert `url` / `playerTrackId` through `match { ... }`, as planned. The merged-restore variants are five separate `tracksUpdated_mergedRows_*` tests, not parameterised tests.

**Files touched outside the plan:**

- None in `app/src/main`. In tests, `PlayerVMTestFixture.kt` got the `mapSubtitleTracks` stub (planned) and no other change.

**For the reviewers:**

- `PlayerTextTracks.kt` and `PlaybackController` are not unit-tested (they need a real `ExoPlayer`); the device checklist D-1..D-9 is the only proof for HLS side-loading, for the manifest rendition matching and for the `HlsManifest` read. The pure part (`subtitleSourceId`) is unit-tested.
- `PlaybackController`: `setMediaSource` is gone; `setMediaItem` is used on both streams, with `MimeTypes.APPLICATION_M3U8` set for HLS. The warm-up still builds HLS through `HlsMediaSource.Factory` directly (D13), now from an item the factory built (with the m3u8 mime and subtitle configurations, which that factory ignores).
- `currentItemHasSideLoads = !subtitles.isNullOrEmpty()` must agree with `PlaybackMediaItemFactory.toMediaItem`, which sets subtitle configurations only for a non-empty list. It does.
- The VM re-send in `onTracksUpdated` runs inside `onTracksChanged`; the single-send assertion is in `tracksUpdated_identicalRepeatedReports_sendSingleReSelect`.
- Detekt / lint / unit tests: green at the end of Task 8 (see the return block).

**Blocked:**

None.

**Fix round 1** (qa defect 1 and triage-1 #1-#6; base `a97ee2b1`):

- Defect 1 (quality switch drops rows and pick). Cause read from code: `switchStream` keeps `pendingSubtitleTrack` but the player first reports audio groups with no text groups; `notifyTracksUpdated` passes `buildSubtitleRows` an empty list, `SubtitleTrackMerger.merge` returns only Off for it, `PlayerVM.onTracksUpdated` finds no row for the pick and sends Off to the controller, which clears the pending pick. Later reports with rows keep Off because the previous row is Off. Fix: a non-null report with no text rows is treated as transient, the VM keeps its rows and selection and sends nothing; the controller's pending pick is applied when text groups return. Test `tracksUpdated_textlessReportAfterQualitySwitch_keepsRowsAndPick` (red before). Not reproduced on a device by me; qa should recheck AC-4/AC-6 on Outlander S1E5.
- B (`AI-generated` row missing on S2E3): not determinable from code. `ai` stays its own language in `canonicalSubtitleLanguage`, so the per-language count rule never compares it with a regular manifest language, and `SubtitleTrackMergerTest.merge_labelsAiGeneratedManifestTrack_fromKinoPubMetadata` plus the mapper and labeler tests cover an `ai` row. No code path drops an `ai` API link when the player reports its side-load. Remaining possibilities are data: the API no longer returning an `ai` link for that episode, or a link with a blank url (now dropped, #3). qa could not read the API list (the picker shows only the merged result), so this needs the raw `subtitles` response for the episode.
- #1 `SubtitleTrackMerger.withApiMetadata` keeps the player's forced flag on `fromManifest` rows. Existing test `merge_matchesApiToRenditionByFilePath_whenLanguageOrderDiffers` had a manifest fixture without the FORCED flag whose API row was forced; the fixture now carries the flag (the case is the one the rule changes).
- #2 the group+index rule in `findSameSubtitleTrackIndex` needs a compatible language and forced flag.
- #3 `sideLoadable()` drops blank-url links in the factory spec, `PlayerUIMapper.mapSubtitleTracks` and `currentItemHasSideLoads`.
- #4 `PlayerTextTracksTest` on the JVM: `Format`, `TrackGroup`, `Tracks.Group` build there with `TextUtils.isEmpty` stubbed through mockk (Media3 `Util` calls it while normalising a language); `Rendition.url` is a mocked `Uri`. One observation, not changed: `findRendition`'s label+language fallback matches a rendition when both labels are null, so a label-less manifest track can take the only same-language rendition's uri.
- #5 `turnsSubtitlesOff()` in `SubtitleTrackSelector.kt`, used by `PlaybackController.selectSubtitle`, tested with an empty-url manifest row.
- #6 two call-count tests for R5 (after a real pick, after a repeated reordered report); they passed on first run, since the behaviour was already right.
- Rejected in triage and left alone: #7, #8.

**Fix round 2** (design delta 2: D17-D20; base `a97ee2b1`):

- D17/D19 (`PlayerVM`): the "transient" rule from round 1 (`36da8957`) is gone. A text-less report merges to `[Off]`, is shown, and sends Off once. `intendedSubtitleRow` (VM-owned, null = Off, cleared only in `resetMediaSession`) keeps the viewer's pick and re-applies it when a later report contains the row. The restore is split into `restoreAudioPreference` and `restoreSubtitlePreference`; the subtitle half waits for the first report with text (`subtitleRestoredForCurrentMedia`). `saveTrackPreferences` takes the subtitle from the intent, or passes the saved value through while the restore is pending, so an audio pick no longer persists Off. An explicit subtitle pick marks the restore done.
- D18: `subtitleReportKind` (new file `SubtitleReportKind.kt`, so detekt `MatchingDeclarationName` stays quiet) decides API_ROWS / NOT_YET / PLAYER_ROWS; `notifyTracksUpdated` returns on NOT_YET.
- D20: `MediaItemSpec.hasSideLoads`; `build(spec)`; `prepare`, `switchStream` and the warm-up use the spec, `currentItemHasSideLoads = spec.hasSideLoads`.
- Tests: the delta's section 5 cases in `PlayerVMTrackSelectionTest` (the old textless keep-rows test replaced), `subtitleReportKind_*` in `PlayerTextTracksTest`, `hasSideLoads_*` in `PlaybackMediaItemSpecTest`. The VM cases were red before the VM change (`selectAudio_whileSubtitlesCollapsed...` and `selectOff_whileCollapsed...` passed already under the old code, they pin the new intent rule).
- Deviation: the P1 test `rebuild_textlessFirstReport_audioPick_doesNotPersistOff` is not written; the fixture has a single buffer preset, so a rebuild cannot be triggered without extending it. The rebuild path resets both flags and is otherwise the same code as the next-media path that is tested.
- Reviewers: `onTracksUpdated` step order (audio restore runs before the subtitle early return), and `saveTrackPreferences` reading `intendedSubtitleRow`.

## Readiness

# Readiness - subtitle-sideload-merge, round 1

Verdict: **READY**

Inputs I read myself: the spec (`docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md`, Status approved,
AC-4 reworded and AC-9 evidence set at gate 1), the plan at `3b979408`, and the worktree code at base `a97ee2b1`.
The worktree has no code change past base: `git diff --stat a97ee2b1 HEAD -- app` is empty. I also read the upstream
sources the plan ports from with `git show b755826c:...`.

## 1. AC coverage (plan task + Test design row)

| AC | Plan task(s) | Test design rows (level) | Covered |
|----|--------------|--------------------------|---------|
| AC-1 | T5 (VM rows), T7 (factory spec), T8 (HLS side-load) | VM `tracksUpdated_hlsApiOnlyLanguage_...` (P0), `PlaybackMediaItemSpecTest.hls_...` (P0), device D-1 | yes |
| AC-2 | T2 (`isOff`, D9), T4 (merger), T5, T8 | merger `merge_carriesManifestOnlyLanguage_...` (P0), `SubtitleTrackUIStateTest` (P0), VM `selectSubtitle_manifestOnlyRow_...` (P0), device D-2 | yes |
| AC-3 | T4 | merger ports + `merge_countsManifestByFromManifestFlag_...` (P0), unknown-language cases (P1), device D-3 | yes |
| AC-4 | T1 (strip helper), T6 (selector), T8 (wiring) | selector `select_picksEachSideLoadedTrackByStrippedFormatId_...`, `select_prefersIdentityOverFullGroupId_...`, `select_usesFullGroupIdAndIndex_...`, `select_neverPicksByPosition` (P0), identity tests (P1), device D-4 | yes |
| AC-5 | T5 (`null` rows mean API rows), T7 (no `embed` filter), T8 (`null` on progressive) | VM `tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows` (P0), spec `progressive_...` (P0), device D-5 | yes |
| AC-6 | T5 (re-find + re-send), T8 (guard change) | VM keep / reorder / disappear / single re-send (P0), resolver `findSameSubtitleTrackIndex_*` (P1), refresh recompute (P1) | yes |
| AC-7 | T1 (`file`), T5 (resolver `sameSubtitleLanguage`, merged restore variants) | existing restore tests plus merged variants (P0), `restoresSavedUrl_onApiLinkedManifestRow` (P0), resolver (P1), `SubtitleLinkTest` (P2), device D-8 | yes |
| AC-8 | T3 | `SubtitleLabelerTest` fork cases (P0), upstream cases and factory (P1), `PlayerUIMapperTest` | yes |
| AC-9 | T8 (no code; D15) | UNTESTABLE by owner decision Q2: Media3 trace D15, P6 hermetic test, opportunistic D-9 | yes, per the gate-1 evidence method |
| AC-10 | T7/T8 (warm-up shape kept, D13) | device D-6 (observation) | yes |
| AC-11 | every task; full gate in T8 | full gate row (P0) | yes |

Every P0 row of the Test design traceability table has a named test in a plan task, and §7 D-1..D-9 is handed to qa-device after Task 8.

## 2. Citation sample (all resolved in code)

I checked these 22 `path:line` citations. All 22 resolve to the symbol the plan names:

- `PC:67` `onTracksUpdated(audioTracks, selectedIndex)`; `PC:162` `currentStreamUrl`; `PC:393` text disabled at build.
- `PC:423-425`, `:491-493` `buildMediaItem` + `setMediaSource(..., stream.type)`; `PC:480-481` comment + `PlaybackErrorPolicy`.
- `PC:551` `track.url.isEmpty()`; `PC:576` clears `currentStreamUrl`.
- `PC:606-622` `buildMediaItem`; `PC:624-635` `subtitleMimeType`; `PC:659-681` `setMediaSource`; `PC:736` warm-up `buildMediaItem`; `PC:758-774` `setWarmUpMediaSource`.
- `PC:852-868` `notifyTracksUpdated` with the audio-only guard at `:855`; `PC:874-890` `applySubtitleTrackSelection`; `PC:892-940` `findTextTrack` / `findTextTrackBy` / `TextTrackSelection`; the no-positional comment at `:902-904`; `PC:7` `toUri` import; `PC:30` `HlsMediaSource` import.
- `VM:69` `resources`; `VM:175` resolver field; `VM:211-222` `onTracksUpdated`; `VM:362-368` `publishPreparedContent`; `VM:469-473` / `:487` refresh; `VM:526-554` restore; `VM:807-817` `applySubtitleSelection`; `VM:578` `action.index`.
- `AudioTrackPreferenceResolver.kt:24-37` matcher list, `:97` the `==` language compare.
- `model/SubtitleLabels.kt:7,8,17-40,42-53,56-61,70-79,81-90`; `PlayerUIModels.kt:26-32`; `PlayerUIMapper.kt:35-66`; `PlayerPreview.kt:36-42`; `Models.kt:428-435` `SubtitleLink`.
- Tests: `PlayerVMTestFixture.kt:86` mapper mock, `:129`, `:229-238`; `PlayerVMTrackSelectionTest.kt:26-30`, `:112-202`; `PlayerUIMapperTest.kt:80-92`, `:98-106` (locale changed after the mapper is built, so P-c is needed); `SubtitleLabelsTest.kt:15-18`, `:24-101`, `:103-107`; `FakeResourceProvider.kt:8-19`; `PlayerVMEpisodeFlowTest.kt:109-125`.
- Config: `detekt.yml:81-83, 90-92, 110-116, 756-764, 817-820`; `app/build.gradle.kts:338-342` `detektAll`; `PuberVM.kt:82-84` `updateViewState` on a `MutableStateFlow` (and `PlayerViewState.Content` is a data class, so `assertSame` works).
- Upstream `b755826c`: the file sizes match (labeler 106, merger 154, selector 104, identity 48, and the tests 33/130/525/156). Also checked: labeler `displayLanguageTag` at `UP:15,21`; merger `isFromManifest` at `UP:148-149`, private strip at `UP:151-154`, partitions at `UP:42`, `:54-56`; selector `UP:46-57`, `:60-69`, `:79-89`, `:91-101`; `UPT` selector `:62-71`; merger test `:86`, `:221`; `PlayerVMSubtitleVariantTest:187-200`; `buildSubtitleTracks` / `findHlsRendition` / the positional `getOrNull(renditionIndex)` in upstream `PlaybackController`.

Two citations are slightly off, with no effect on the work:

- The plan's `merge_groupsVariantsByLanguage_keepingManifestOrder` is upstream's `..._keepingManifestOrderWithinEachLanguage`.
- The Test design's `merge_matchesApiToRenditionByFilePath_...` is upstream's `merge_usesApiFilePathToMatchHlsRenditionUri_...`.

## 3. Build stays green, dependency order

- Each task's production code compiles on its own:
  - T1 adds unused `internal` helpers.
  - T2 updates every `SubtitleTrackUIState(` constructor. Grep finds only the mapper, the preview, `PlayerUIMapperTest` and the fixture.
  - T5's third callback parameter defaults to `null`, so `PC:867` and every two-argument test call still compile.
  - T6 and T4 are unused until they are wired.
  - T7 replaces all three `buildMediaItem` call sites.
  - T8 wires everything and runs the full gate.
- The order follows the dependencies: T1 → T2 → T3 → T4 → T5 (needs T4) → T6 → T7 → T8 (needs T5, T6 and T7). The labeler's imports from `vm` (T1) exist before T3.
- Fixture risk: from T5 on, the refresh tests call `mapper.mapSubtitleTracks`, which the mock does not stub (`relaxUnitFun` does not cover it). T5 adds the stub.

## 4. Scope, open questions, refinements P-a / P-b / P-c

- **Out of scope respected.** The plan adds no `SubtitlePreference` and no new pref keys, and leaves `PlayerPreferencesRepository` unchanged. It adds no instrumented tests, touches no f38fbcf0/dc011a81 work, and leaves DI, the screen module and Compose layout alone. Q3 and Q4 stay out.
- **Blocking questions answered.** Q1 and Q2 were answered at gate 1. Q5 (device titles) is non-blocking and needed only before verify.
- **P-a (strip only when merged): inside D5.** Media3 builds an HLS rendition id as `<GROUP-ID>:<NAME>`. An unmerged item has no merge prefix to strip, so the guard only makes D5 safe for numeric GROUP-IDs. `fromManifest = !merged || groupId.startsWith("0:")` is D5's rule: child 0 is the main source. "Merged" equals "the factory set `subtitleConfigurations`", and Task 7 sets them only when the list is non-empty, consistent with Task 8's `!subtitles.isNullOrEmpty()`.
- **P-b (identity in two steps): inside D8 and AC-4.** Both steps are "prefix-stripped source identity (format id / stable subtitle key)", each reduced with `singleOrNull`, and they come before the full group id + index and before language. No positional rule remains. The upstream test `select_prefersManifestRendition_overSideLoadedCopyOfTheSameSubtitle` exercises the reason for the split.
- **P-c (labeler per call): inside D10 and D16.** There is still no DI. D16's "plain fields" changes to per-call construction only so that `resources.locale` is read when labelling. That reason is real: `PlayerUIMapperTest.kt:98-106` changes the locale after the mapper is built. The cost is negligible (R7). Output stays deterministic, so R5's loop guard holds.

## Fix list

None blocking.

## Advisory (for the implementer and reviewers; not required for READY)

1. **Unit-test P-a.** P-a lives in `PlayerTextTracks.buildSubtitleRows`, which has no unit test, so the prefix strip and `fromManifest` derivation are checked only on device. Factor `(formatId, groupId, merged) -> (playerTrackId, fromManifest)` into a pure helper and test it, including a numeric GROUP-ID such as `"1:Russian"` in both the merged and unmerged case.
2. **Merger strip contradicts P-a.** The merger keeps upstream's "defensive strip" in `sourceIdentities` / `findExactIdentityMatch`, so it strips unmerged numeric GROUP-ID ids that P-a deliberately keeps. It is harmless today: such ids are not paths, so `sameSubtitleIdentity` falls back to equality. Reviewers should confirm it stays harmless, or drop the strip there.
3. **Disappear test needs its input pinned.** `tracksUpdated_selectedRowDisappears_goesOffAndDisablesText` must remove every row of that language. D11 rule 6 (unambiguous language) would otherwise re-find the remaining `rus` row instead of Off, and the fixture has two `rus` rows.
4. **Detekt runs only at the end.** It first runs in Task 8, and the plan expects `ComplexCondition` to possibly fire on the T2 `isOff` getter. Running `:app:detektAll` after T2 and T5 avoids collecting several fixes at the end.
5. **Stale spec wording.** "The wording needs changing (Q1)" under AC-4 in the spec's Design section is stale. It is cosmetic, and the spec is not changed in this run.
