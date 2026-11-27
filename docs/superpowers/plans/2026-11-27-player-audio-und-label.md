# Audio row shows the raw language code "und" for an undetermined-language track - plan

**Spec:** `docs/superpowers/specs/2026-11-27-player-audio-und-label.md`
**Goal:** Treat the language "und" like a missing language wherever Media3 formats become audio or subtitle rows, so an unlabelled track reads the localized fallback and never overwrites the stored dubbing.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh`. Both run in the worktree `/home/dev/orca/workspaces/Puber/player-audio-und-label`; never `./gradlew` there. `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched unit tests: `./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`.
- Instrumented tests run on the emulator only: `ssh mac-emu start`, `adb connect host.docker.internal:5555`, `tools/emulator-resource-lock.sh acquire host.docker.internal:5555`, then `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest`; afterwards `tools/emulator-resource-lock.sh release host.docker.internal:5555 <token>` and `ssh mac-emu stop` if `status` shows no other lease.
- One task = green fast gate + green tests of the touched code + one commit, message: one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- Red run: every `red` test of a task is written and run before that task's production edit and must fail on an assertion; record the failing line in the Implementation record. Tests that call `knownLanguageOrNull` are written after the helper exists.
- No live KinoPub API probing (AGENTS.md).
- Out of scope (spec Open questions): the hardcoded "Unknown" API audio language in `model/PlayerUIMapper.kt:20-33` (Q2), `SubtitleTrackMerger.orderingLanguage` (`vm/SubtitleTrackMerger.kt:104-105`, Q3), migrating stored `audioLang = "und"` values, any change to `vm/PlayerVM.kt` or `vm/PlaybackController.kt`.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.

Paths below are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless they start with `app/`. Unit tests: `app/src/test/kotlin/com/kino/puber/ui/feature/player/` (`UT/` below); instrumented: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/` (`IT/` below).

---

## Tasks

### Task 1: Recognise "und" as no language and keep it out of audio rows and language matching

**Covers:** AC-1, AC-2, AC-3, AC-5, AC-6
**Files:**
- Modify: `model/SubtitleLanguage.kt` - add `internal fun knownLanguageOrNull(language: String?): String?` above `canonicalSubtitleLanguage` (`:13`): trim and lowercase (`Locale.ROOT`); null when empty or when the part before the first `-` or `_` equals `"und"` (private const); otherwise return `language` unchanged (not trimmed, not canonicalised). In `sameSubtitleLanguage` (`:8-11`) replace the `isBlank` check: `knownLanguageOrNull` on both sides, false when either is null, then compare `canonicalSubtitleLanguage` of the two as now. Signature unchanged. `canonicalSubtitleLanguage` unchanged.
- Modify: `vm/PlayerAudioTracks.kt` - in `buildAudioRows` (`:7-16`): `val language = knownLanguageOrNull(format.language)`, `name = format.label ?: language`, `language = language.orEmpty()`; `label` and `isFallbackLabel = name == null` unchanged. Update the KDoc (`:6`) only if it stops being true.
- Test: `UT/vm/PlayerAudioTracksTest.kt` (helpers `format` `:34`, `rows` `:40`; TextUtils stub `:23-32`)
- Test: `UT/vm/PlayerVMTrackSelectionTest.kt` (pattern `:104-131`; `STORED_AUDIO_LABEL` `:37`; defaults `UT/vm/PlayerVMTestFixture.kt:107-108`, `startedVM` `:193`)
- Test: `UT/vm/SubtitleTrackIdentityTest.kt` (next to `sameSubtitleLanguage_matchesRuAndRus_rejectsBlank` `:62`)
- Test: `UT/vm/SubtitleTrackSelectorTest.kt` (`selector` `:10`; pattern `select_matchesByLanguage_onlyWhenUnambiguous` `:89`)
- Test: `IT/PlaybackControllerNetworkTracksTest.kt` (pattern `unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback` `:159`; `harness.useMediaRoutes(required = setOf("progressive"))` as in `IT/PlaybackControllerDeviceTest.kt:34`, defined `IT/PlaybackControllerHarness.kt:247`)

**Consumes:** `vm/PlayerVM.kt:1966` keeps the stored audio when `isFallbackLabel` (no edit); `vm/PlaybackController.kt:872` routes audio formats through `buildAudioRows` (no edit); selector language paths `vm/SubtitleTrackSelector.kt:55-56,106-117` go through `sameSubtitleLanguage` (no edit).
**Produces:** `knownLanguageOrNull(language: String?): String?` in `com.kino.puber.ui.feature.player.model`, used by Task 2.

Tests (names from `test-design.md`):

| Test | Priority | Fixture / assertion |
|---|---|---|
| `PlayerAudioTracksTest.buildAudioRows_undeterminedLanguage_usesFallbackString` | P0 red | `rows(format("und"), format("UND"), format("und-Latn"))`: labels are fallback 1..3, all `isFallbackLabel`, languages `""` |
| `PlayerAudioTracksTest.buildAudioRows_labelledUndeterminedLanguage_keepsLabelWithoutLanguage` | P0 red | `format("und", "English")`: label "English", `isFallbackLabel` false, language `""` |
| `PlayerVMTrackSelectionTest.selectAudioTrack_undeterminedRowFromPlayer_keepsStoredAudioPreference` | P0 red, failing-first | helper `startedWithUndeterminedAudioRow()`: stored `"rus"` / `STORED_AUDIO_LABEL`, row = `buildAudioRows` of one `Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("und").build()` with `{ n -> "Track $n" }`, reported via `callbackSlot.captured.onTracksUpdated(rows, 0)`; `SelectAudioTrack(0)`; `verify { interactor.saveTrackPreferences(42, "rus", STORED_AUDIO_LABEL, any()) }` |
| `PlayerVMTrackSelectionTest.selectSubtitle_whileAudioRowIsUndetermined_keepsStoredAudioPreference` | P0 red | same helper, `SelectSubtitle(1)`, same `verify` |
| `SubtitleTrackIdentityTest.sameSubtitleLanguage_rejectsUndetermined` | P0 red | `("und","und")`, `("und","UND")`, `("und","ru")` all false |
| `SubtitleTrackSelectorTest.select_doesNotMatchByLanguage_whenBothAreUndetermined` | P0 red | row `SubtitleTrackUIState(label = "und", language = "und", url = "")`, candidates `listOf(PlayerTextTrack("", 0, 0, language = "und"))`: `assertNull(selector.select(...))` |
| `PlaybackControllerNetworkTracksTest.progressiveUndeterminedAudio_reportsLocalizedFallbackRow` | P1 red | prepare `/media/progressive.mp4`, await a `PlaybackEvent.Tracks`; first event's first audio row: label `harness.targetContext.getString(R.string.player_audio_track_fallback, 1)`, `isFallbackLabel` true, `language` `""`; `probe.error.get() == null` |
| `SubtitleTrackIdentityTest.knownLanguageOrNull_treatsUndeterminedAsUnknown` | P1 | null for `null`, `""`, `" "`, `"und"`, `"UND"`, `" und "`, `"und-Latn"`, `"und_US"` |
| `SubtitleTrackIdentityTest.knownLanguageOrNull_keepsRealLanguagesVerbatim` | P0 guard | `"ru"`, `"rus"`, `"en-US"`, `"ai"`, `"undefined"` returned unchanged (`assertEquals` same string) |

Existing guards that must stay green: `PlayerAudioTracksTest.buildAudioRows_labelThenLanguage_keepsManifestName` (`:59`), `PlayerVMTrackSelectionTest.selectTrack_savesLangToPrefs` (`:99`), `SubtitleTrackSelectorTest.select_matchesByLanguage_onlyWhenUnambiguous` (`:89`).

- [x] In `PlayerVMTrackSelectionTest`, stub `TextUtils` only around the `Format` build inside the helper (`mockkStatic(TextUtils::class)` / `every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }` / `unmockkStatic` in `finally`), as in `PlayerAudioTracksTest.kt:23-32`
- [x] Write the P0 red unit tests above; run them on the unchanged code; each fails on an assertion (the failing-first test saves `("und", "und")`)
- [x] Write the instrumented test; run it on the emulator on the unchanged code (constraints above); it fails with label "und"
- [x] Implement `knownLanguageOrNull`, `sameSubtitleLanguage` and `buildAudioRows` as listed; add the two `knownLanguageOrNull` tests
- [x] Run the four unit classes and the instrumented class green; fast gate; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.PlayerAudioTracksTest' --tests 'com.kino.puber.ui.feature.player.vm.PlayerVMTrackSelectionTest' --tests 'com.kino.puber.ui.feature.player.vm.SubtitleTrackIdentityTest' --tests 'com.kino.puber.ui.feature.player.vm.SubtitleTrackSelectorTest'` passes, the `itest` run of `PlaybackControllerNetworkTracksTest` passes on `host.docker.internal:5555`, and the fast gate is green.

---

### Task 2: Name "und" subtitle rows by rendition language or position instead of "UND"

**Covers:** AC-4, AC-8
**Files:**
- Modify: `vm/PlayerTextTracks.kt` - in `buildSubtitleRow` (`:46`): `language = knownLanguageOrNull(format.language) ?: knownLanguageOrNull(renditionFormat?.language).orEmpty()`. In `toPlayerTextTracks` (`:87`): `language = knownLanguageOrNull(format.language)`. `findRendition` (`:59-74`) unchanged: its language matcher (`:69`) goes through `sameSubtitleLanguage`. Import `com.kino.puber.ui.feature.player.model.knownLanguageOrNull` next to `:10`.
- Modify: `model/SubtitleLabeler.kt` - `rawLanguageLabel` (`:42-45`) becomes `knownLanguageOrNull(track.language)?.trim()?.uppercase(Locale.ROOT)`, so "und" falls through to `unknownLabel` (`:50`).
- Test: `UT/vm/PlayerTextTracksTest.kt` (helpers `format` `:38`, `group` `:51`, `rendition` `:56`; patterns `manifestRow_matchesRenditionById_andTakesItsUri` `:109`, `toPlayerTextTracks_flattensWithGroupIndexAndForcedFlag` `:170`)
- Test: `UT/model/SubtitleLabelerTest.kt` (`labeler` `:21`, `track` helper `:236`; neighbour `unresolvableCodes_fallBackToTheCode_andMissingOnesToAPosition` `:71`)

**Consumes:** `knownLanguageOrNull` from Task 1.
**Produces:** nothing new.

| Test | Priority | Fixture / assertion |
|---|---|---|
| `SubtitleLabelerTest.apply_undeterminedLanguageWithoutLabel_readsAsUnknownOrdinal` | P0 red | `labeler.apply(listOf(track("und")))` label is `"Субтитры 1"` |
| `PlayerTextTracksTest.manifestRow_undeterminedFormatLanguage_takesRenditionLanguage` | P0 red | as `:109`: `group("0:subs:1", format("0:subs:1", language = "und"))`, rendition `format("subs:1", language = "rus")`, `merged = true`, matched by id: row language `"ru"` (`Format.Builder` normalises "rus" to "ru") |
| `PlayerTextTracksTest.toPlayerTextTracks_undeterminedLanguage_isNull` | P1 red | one group with `format("t", language = "und")`: `toPlayerTextTracks().single().language` is null |

Existing guards that must stay green: `SubtitleLabelerTest.unresolvableCodes_fallBackToTheCode_andMissingOnesToAPosition` (`:71`), `SubtitleLabelerTest.apply_preservesUnknownLanguageInformation_beforePositionalFallback` (`:220`).

- [x] Write the three red tests; run them on the Task 1 code (these paths are untouched there); each fails on an assertion
- [x] Apply the two edits; run both classes green; fast gate
- [x] Run the full gate (AC-8); commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.PlayerTextTracksTest' --tests 'com.kino.puber.ui.feature.player.model.SubtitleLabelerTest'` and the full gate pass.

---

AC-7 (spec Manual checks, box `192.168.1.110:5555`, `com.kino.puber.stage` only) is run by `qa-device` in stage verify; no task here.

## Implementation record

<!-- owner: implementer -->

**Commits:**
- Task 1: Treat the undetermined language as no language in audio rows and subtitle language matching
- Task 2: Name undetermined subtitle rows by rendition language or position instead of UND

**Red run** (P0 `red` tests on the unchanged code):
- red: PlayerAudioTracksTest.buildAudioRows_undeterminedLanguage_usesFallbackString - AssertionFailedError at PlayerAudioTracksTest.kt:62 (label "und")
- red: PlayerAudioTracksTest.buildAudioRows_labelledUndeterminedLanguage_keepsLabelWithoutLanguage - AssertionFailedError at :76 (language "und")
- red: PlayerVMTrackSelectionTest.selectAudioTrack_undeterminedRowFromPlayer_keepsStoredAudioPreference - AssertionError at :134 (saved ("und","und"))
- red: PlayerVMTrackSelectionTest.selectSubtitle_whileAudioRowIsUndetermined_keepsStoredAudioPreference - AssertionError at :143
- red: SubtitleTrackIdentityTest.sameSubtitleLanguage_rejectsUndetermined - AssertionFailedError at :70
- red: SubtitleTrackSelectorTest.select_doesNotMatchByLanguage_whenBothAreUndetermined - AssertionFailedError at :113
- red: SubtitleLabelerTest.apply_undeterminedLanguageWithoutLabel_readsAsUnknownOrdinal - AssertionFailedError at :77 ("UND")
- red: PlayerTextTracksTest.manifestRow_undeterminedFormatLanguage_takesRenditionLanguage - AssertionFailedError at :135
- red (P1): PlayerTextTracksTest.toPlayerTextTracks_undeterminedLanguage_isNull - AssertionFailedError at :201
- red (P1, emulator): PlaybackControllerNetworkTracksTest.progressiveUndeterminedAudio_reportsLocalizedFallbackRow - expected:<[Track 1]> but was:<[und]> (itest-1.xml: 13 cases, 1 failure)
- Green after the change: itest-2.xml, 13 cases, 0 failures. The two `knownLanguageOrNull` tests need the helper and were written after it.

**Deviations from the plan:** none. `PlayerVMTrackSelectionTest` stubs `TextUtils` only around the `Format` build, as the plan says.

**Files touched outside the plan:** none.

**For the reviewers:** `knownLanguageOrNull` returns the input verbatim (no trim, no canonicalisation); `canonicalSubtitleLanguage` now shares the `UNDETERMINED_LANGUAGE` constant. `check.full` green (baseline-profile tooling script all PASS).

**Fix round 1** (review findings #1 codex, #2 state-path F1): an HLS row that took the rendition language ("ru") no longer reached its untagged candidate (language null), and an API row with "und" matched nothing. `SubtitleTrackSelector`: an undetermined row language counts as blank; an untagged candidate passes the semantic filter, so the identity matchers see it; `matchByLanguage` still needs a tagged candidate and a determined row language. `PlaybackController.applySubtitleTrackSelection` passes `knownLanguageOrNull(track.language).orEmpty()` to `setPreferredTextLanguage` (deviation from the plan's out-of-scope line, required by finding #2).
- red: SubtitleTrackSelectorTest.select_resolvesManifestRowTakingRenditionLanguage_toItsUntaggedCandidate - AssertionFailedError at :175
- red: SubtitleTrackSelectorTest.select_resolvesUndeterminedApiRow_toItsUntaggedSideLoad - AssertionFailedError at :198
- guard: select_doesNotPickUntaggedCandidateByLanguage_forTaggedRow - green on base, pins that the relaxed filter does not let `matchByLanguage` pick an untagged track
- Green after: `com.kino.puber.ui.feature.player.*` unit tests, `:app:compileDevDebugKotlin`, `:app:compileInstrumentationDebugAndroidTestKotlin`; emulator `PlaybackControllerNetworkTracksTest`: 13 cases, 0 failures (`.flow/runs/player-audio-und-label/itest-fix1.xml`); lease released, emulator stopped.

**Blocked:** none.

## Readiness

Verdict: **READY**

Read: spec and plan at worktree HEAD `3e99f133` (base `cb7fcd45`), `test-design.md`, `state.md`. Paths are under `app/src/main/java/com/kino/puber/ui/feature/player/` unless they start with `app/`, `player-test-fixtures/` or `Makefile`.

### Checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | Yes. AC-1, AC-2, AC-3, AC-5, AC-6: Task 1; AC-4, AC-8: Task 2; AC-7: Manual check 1, run by `qa-device` in verify |
| Tasks ordered by dependency, build green after each | Yes. Task 1 adds `knownLanguageOrNull` and its two callers; Task 2 only consumes it. Task 2 red tests fail on Task 1 code: `rawLanguageLabel` and `PlayerTextTracks.kt:46,87` are untouched by Task 1 |
| P0 rows: defect named, `red`/`guard`, test name in backticks | Yes, all P0 rows |
| Red tests can fail on an assertion on base | Yes: `PlayerAudioTracks.kt:9,13-14` keeps "und"; `PlayerVM.kt:1966-1977` then saves ("und", "und"); `SubtitleLanguage.kt:8-11` treats "und" = "und"; `SubtitleLabeler.kt:42-45` yields "UND"; `SubtitleTrackSelector.kt:110-116` matches by language |
| AC-6 fixture really carries "und" | Yes: both `mdhd` boxes of `player-test-fixtures/src/main/assets/player-fixtures/progressive.mp4` decode to "und" (one `soun` track) |
| Out of scope respected | Yes: no edit to `PlayerVM.kt`, `PlaybackController.kt`, `model/PlayerUIMapper.kt:20-33` (Q2), `SubtitleTrackMerger.kt:104-105` (Q3) |
| Blocking questions answered | Q1 answered (option B); Q2, Q3 non-blocking, answered |
| Device rules | Instrumented run on the emulator with the `instrumentation` flavor (package `.instrumentation`, `app/build.gradle.kts:239-241`), so nothing but `com.kino.puber.stage` touches the box; lease and `ssh mac-emu stop` stated |

### Citations sampled

| Citation | Resolves to |
|---|---|
| `vm/PlayerAudioTracks.kt:9,12-14` | `name = format.label ?: format.language`, `language`, `isFallbackLabel = name == null` |
| `vm/PlayerVM.kt:1966` | `keepStoredAudio = audioTrack?.isFallbackLabel == true` |
| `vm/PlayerTextTracks.kt:46,69,87` | row language, `findRendition` language matcher, `toPlayerTextTracks` language |
| `model/SubtitleLanguage.kt:8-11,13,22` | `sameSubtitleLanguage` with `isBlank`, `canonicalSubtitleLanguage`, `it != "und"` |
| `model/SubtitleLabeler.kt:42-45,50` | `rawLanguageLabel`, `unknownLabel(position + 1)` |
| `vm/SubtitleTrackSelector.kt:55-56,106-117` | semantic filter, `matchByLanguage` |
| `vm/PlaybackController.kt:872,905` | `buildAudioRows(...)`, `setPreferredTextLanguage(track.language)` |
| `vm/AudioTrackPreferenceResolver.kt:201,210` | literal `track.language == preferredLang` compares |
| `app/src/test/.../vm/PlayerVMTrackSelectionTest.kt:37,99,104-131` | `STORED_AUDIO_LABEL`, `selectTrack_savesLangToPrefs`, fallback-row tests and helper |
| `app/src/test/.../vm/PlayerAudioTracksTest.kt:23-32,34,40` | `TextUtils` stub, `format`, `rows` |
| `app/src/test/.../vm/SubtitleTrackIdentityTest.kt:62`, `SubtitleTrackSelectorTest.kt:10,89` | `sameSubtitleLanguage_matchesRuAndRus_rejectsBlank`, `selector`, `select_matchesByLanguage_onlyWhenUnambiguous` |
| `app/src/test/.../vm/PlayerTextTracksTest.kt:38,51,56,109,170`, `model/SubtitleLabelerTest.kt:21,71,220,236` | helpers and pattern tests as named |
| `app/src/androidTestInstrumentation/.../PlaybackControllerNetworkTracksTest.kt:159`, `PlaybackControllerDeviceTest.kt:34`, `PlaybackControllerHarness.kt:247` | fallback-row test, `useMediaRoutes(required = setOf("progressive"))`, its definition |
| `app/src/main/res/values-en/strings.xml:301-302` | `player_subtitle_unknown`, `player_audio_track_fallback` |

All resolve.

### Notes (non-blocking)

1. AC-4 says the row language is "rus"; `PlayerTextTracksTest.manifestRow_undeterminedFormatLanguage_takesRenditionLanguage` asserts "ru" because `Format.Builder` normalises it. Amend the AC-4 wording to the normalised code before the gate, or the gate reads the test as proving AC-4 with that normalisation.
2. Spec AC-6 and `test-design.md` give `make DEVICE=host.docker.internal:5555 itest`, which builds the `dev` variant (`Makefile:19,31,138`) and does not run `androidTestInstrumentation`. The plan's command (`FLAVOR=instrumentation GRADLE=./tools/agentw`) is the one to use.
3. `make itest` can exit 0 on an incomplete run (run `itest-incomplete-run-green`). Task 1 "Done when" should read the case count in the JUnit XML under `app/build/outputs/androidTest-results/connected/`, as `docs/superpowers/plans/2026-11-27-player-callback-gate.md:16` does.

