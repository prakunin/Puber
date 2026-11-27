# Play manual quality from the hls4 stream with a max video size, keeping manifest subtitles and audio names - plan

**Spec:** `docs/superpowers/specs/2026-11-27-manual-quality-hls4.md`
**Goal:** A manual quality pick plays the picked file's `hls4` stream pinned to its size, and unlabelled audio gets a localized label that never overwrites the stored dubbing.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh`. Both run in the worktree `/home/dev/orca/workspaces/Puber/manual-quality-hls4`; never `./gradlew` there. `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched unit tests: `./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`.
- Instrumented tests run on the emulator only (state.md Decisions): `ssh mac-emu start`, `adb connect host.docker.internal:5555`, `tools/emulator-resource-lock.sh acquire host.docker.internal:5555`, then `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest`; afterwards `tools/emulator-resource-lock.sh release host.docker.internal:5555 <token>` and `ssh mac-emu stop` if `status` shows no other lease.
- One task = green fast gate + green tests of the touched code + one commit, message: one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- No live KinoPub API probing; device checks only at human pace (AGENTS.md).
- Out of scope (spec Open questions): the corner-draw glitch (Q3), cleanup of saved "Track 1" labels (D1), the "Unknown" language in `PlayerUIMapper.kt:24` (D2), `warmUpNext` (`PlaybackController.kt:700`).
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.

Paths below are under `app/src/main/java/com/kino/puber/` unless they start with `app/`, `player-test-fixtures/` or `tools/`. Unit tests: `app/src/test/kotlin/com/kino/puber/`; instrumented: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/` (`IT/` below).

---

## Tasks

### Task 1: Manual candidates play the picked file's hls4 first and carry its size cap

**Covers:** AC-1
**Files:**
- Modify: `domain/interactor/player/PlayerInteractor.kt` - add `internal data class VideoSizeCap(val width: Int, val height: Int)` next to `StreamCandidate` (`:26`) and `val videoCap: VideoSizeCap? = null` as its last property. In `manualStreamCandidates` (`:242-251`) keep the picked `VideoFile` (`uniqueFiles.getOrNull(qualityIndex - 1) ?: files.first()`), compute `cap = file.h?.let { VideoSizeCap(file.w ?: Int.MAX_VALUE, it) }`, emit `hls4` (cap), `hls` (cap), `http` (PROGRESSIVE, no cap). `autoStreamCandidates` (`:233`) and the `copy(url=...)` / `distinctBy` step in `selectStreamCandidates` (`:218-230`) unchanged. `VideoFile.w/h`: `data/api/models/Models.kt:579-587`.
- Test: `domain/interactor/player/PlayerInteractorTest.kt`

**Consumes:** `VideoFile`, `VideoUrl` (`data/api/models/Models.kt:579,590`); `playerPreferencesRepository.preferSurroundAudio` stub pattern at `PlayerInteractorTest.kt:346`.
**Produces:** `VideoSizeCap`; `StreamCandidate(url, type, videoCap = null)`.

- [x] Failing first: rename `selectStreamCandidates_manual_prefersSelectedQualityHls` (`PlayerInteractorTest.kt:316`) to `selectStreamCandidates_manual_prefersSelectedQualityHls4`, expect `1080-hls4.m3u8`, `1080.m3u8`, `1080.mp4`; run it on unchanged code and record the failure on the first element (Implementation record, Red run)
- [x] Add `w = 1920, h = 1080` to that test's 1080 file and expect `VideoSizeCap(1920, 1080)` on both HLS candidates, none on `http`
- [x] Add `selectStreamCandidates_manualWithSurround_keepsVideoCap`: surround on, distinct `hls4`/`hls`, both rewritten to `?ac3default=1` and still capped
- [x] Add `selectStreamCandidates_manualFileWithoutWidth_capsHeightOnly`: `w = null, h = 720` gives `VideoSizeCap(Int.MAX_VALUE, 720)`
- [x] Add `selectStreamCandidates_manualFileWithoutHeight_hasNoCap`: `h = null` gives `videoCap == null` on every candidate
- [x] Add `selectStreamCandidates_manualIndexOutOfRange_capsFallbackFile`: `qualityIndex` past the list uses `files.first()` and its own `w/h`
- [x] Implement; `selectStreamCandidates_auto_returnsEveryProtocolInFallbackOrder` (`:294`) stays unchanged and green (data-class equality with default `null` proves no cap on Auto)
- [x] Run the fast gate and `PlayerInteractorTest`; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.domain.interactor.player.PlayerInteractorTest'` passes with the five tests above plus the unchanged Auto test.

---

### Task 2: The controller pins video to the candidate's cap on prepare and switch

**Covers:** AC-2, AC-3
**Files:**
- Create: `ui/feature/player/vm/VideoCapParameters.kt` - `internal fun <B : TrackSelectionParameters.Builder> B.applyVideoCap(cap: VideoSizeCap?): B = apply { ... }`: cap set -> `setMaxVideoSize(cap.width, cap.height)`, `setForceHighestSupportedBitrate(true)`; null -> `clearVideoSizeConstraints()`, `setForceHighestSupportedBitrate(false)`. The generic receiver keeps `DefaultTrackSelector.Parameters.Builder` chainable in `prepare`.
- Modify: `ui/feature/player/vm/PlaybackController.kt` - in `prepare` (`:402`) the selector parameters (`:425-432`): `.setExceedVideoConstraintsIfNecessary(true)` (was `false` at `:426`) and `.applyVideoCap(stream.videoCap)` before `.build()`. In `switchStream` (`:526`) replace `player.trackSelectionParameters = savedTrackParams` (`:543`) with `savedTrackParams.buildUpon().applyVideoCap(stream.videoCap).build()`. `disableAc3AndRetry` (`:630`) unchanged.
- Modify: `IT/PlaybackControllerHarness.kt` - `stream(pathAndQuery, videoCap: VideoSizeCap? = null)` (`:190`) passes it to `StreamCandidate`; `prepare(..., videoCap: VideoSizeCap? = null)` (`:201`) forwards it to `stream`.
- Modify: `IT/PlaybackControllerReads.kt` - after `selectedAudioLanguage()` (`:25`): `selectedVideoHeight(): Int?` (first selected track of the `C.TRACK_TYPE_VIDEO` groups in `player.currentTracks`, via `isTrackSelected`), `maxVideoSize(): Pair<Int, Int>?` (`trackSelectionParameters.maxVideoWidth to maxVideoHeight`), `forceHighestSupportedBitrate(): Boolean`.
- Test: `ui/feature/player/vm/VideoCapParametersTest.kt` (new, JUnit 5), `IT/PlaybackControllerNetworkTracksTest.kt`

**Consumes:** `VideoSizeCap`, `StreamCandidate.videoCap` (Task 1); fixture `player-test-fixtures/src/main/assets/player-fixtures/hls/master.m3u8` (variants 640x360, 1280x720), served at `/media/hls/master.m3u8` by `harness.commonHlsRoutes()` (`IT/PlaybackControllerRoutes.kt:34`; usage pattern `IT/PlaybackControllerNetworkTracksTest.kt:41-46`).
**Produces:** `applyVideoCap`; harness `videoCap` parameters; the three reads.

- [x] `VideoCapParametersTest` from `TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon()`: `applyVideoCap_cap_setsMaxSizeAndForcesHighestBitrate`, `applyVideoCap_null_clearsEarlierCap` (start from parameters built with a cap; expect `Int.MAX_VALUE` both and force-highest false), `applyVideoCap_keepsUnrelatedParameters` (text disabled and preferred audio language `"es"` survive)
- [x] `hlsVideoCap_switchStreamReplacesCap`: prepare master with `VideoSizeCap(640, 360)`, await READY and `selectedVideoHeight() == 360`; `switchStream(harness.stream(master, VideoSizeCap(1280, 720)), null)`, await 720; `switchStream(harness.stream(master), null)`, await `maxVideoSize() == Int.MAX_VALUE to Int.MAX_VALUE` and `!forceHighestSupportedBitrate()`; no unknown requests
- [x] `hlsVideoCap_belowEveryVariant_stillSelectsVideo`: prepare with `VideoSizeCap(320, 180)`, await READY and `selectedVideoHeight() == 360`
- [x] Implement the extension and the controller change
- [x] Run the fast gate and `VideoCapParametersTest`; run `PlaybackControllerNetworkTracksTest` on the emulator (Global constraints), where `hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText` (`:39`) and `hlsSwitch_preservesPositionIntentTracks_andLastStreamWins` (`:110`) stay green; commit

**Done when:** `VideoCapParametersTest` passes (3 tests) and the itest run reports `PlaybackControllerNetworkTracksTest` green including both new tests.

---

### Task 3: Unlabelled audio rows read a localized fallback and are marked

**Covers:** AC-8
**Files:**
- Modify: `ui/feature/player/model/PlayerUIModels.kt` - `AudioTrackUIState` (`:20`): add `val isFallbackLabel: Boolean = false` last (positional constructions in tests and `component/PlayerPreview.kt:31` keep compiling).
- Create: `ui/feature/player/vm/PlayerAudioTracks.kt` - `internal fun buildAudioRows(formats: List<Format>, fallbackLabel: (Int) -> String): List<AudioTrackUIState>`; per index: `label = format.label ?: format.language ?: fallbackLabel(index + 1)`, `language = format.language.orEmpty()`, `isFallbackLabel` true only on the last branch.
- Modify: `ui/feature/player/vm/PlaybackController.kt` - in `notifyTracksUpdated` (`:864`) replace the `mapIndexed` block (`:871-879`, `"Track ${index + 1}"` at `:873`) with `buildAudioRows(audioGroups.map { it.getTrackFormat(0) }) { n -> context.getString(R.string.player_audio_track_fallback, n) }` (`context`: `:161`).
- Modify: `app/src/main/res/values/player.xml` - after `player_subtitle_unknown` (`:47`): `<string name="player_audio_track_fallback">Дорожка %1$d</string>`.
- Modify: `app/src/main/res/values-en/strings.xml` - after `player_subtitle_unknown` (`:301`): `<string name="player_audio_track_fallback">Track %1$d</string>`.
- Test: `ui/feature/player/vm/PlayerAudioTracksTest.kt` (new, JUnit 5)

**Consumes:** `FakeResourceProvider` (`app/src/test/kotlin/com/kino/puber/util/FakeResourceProvider.kt`; `getString(resId, n)` returns `string_<resId>_<n>`); the `TextUtils` static stub of `PlayerTextTracksTest.kt:27-36` (`Format` normalises language through it).
**Produces:** `AudioTrackUIState.isFallbackLabel`, `buildAudioRows`, `R.string.player_audio_track_fallback`.

- [x] `buildAudioRows_unlabelledGroup_usesFallbackString`: two formats with no label and no language; labels `fake.getString(R.string.player_audio_track_fallback, 1)` and `..., 2)`, `isFallbackLabel` true
- [x] `buildAudioRows_labelThenLanguage_keepsManifestName`: label+language -> label; language only -> language; `isFallbackLabel` false; `index` follows input order
- [x] Implement; run the fast gate and `PlayerAudioTracksTest`, `PlayerTextTracksTest`; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.PlayerAudioTracksTest'` passes (2 tests) and `rg -n '"Track \$' app/src/main` finds nothing.

---

### Task 4: A pick on a fallback audio row keeps the stored audio preference

**Covers:** AC-9
**Files:**
- Modify: `ui/feature/player/vm/PlayerVM.kt` - `saveTrackPreferences` (`:1955`): when `audioTrack?.isFallbackLabel == true`, pass `interactor.getPreferredAudioLang(params.itemId)` and `interactor.getPreferredAudioLabel(params.itemId)` (`PlayerInteractor.kt:337,341`) instead of the row's values (`PlayerVM.kt:1967-1968`); subtitle argument unchanged. Callers `applyAudioTrackSelection` (`:907`) and `applySubtitleSelection` (`:917`) unchanged.
- Test: `ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`

**Consumes:** `AudioTrackUIState.isFallbackLabel` (Task 3); fixture `PlayerVMTestFixture.kt` (`startedVM()` `:193`, `callbackSlot` `:77`, preference stubs `:107-108`, item id 42).
**Produces:** nothing new.

- [x] `selectSubtitle_whileAudioRowIsFallback_keepsStoredAudioPreference`: stub `getPreferredAudioLang(42)` = `"rus"`, `getPreferredAudioLabel(42)` = `"01. Дубляж (RUS)"`; `startedVM()`; `callbackSlot.captured.onTracksUpdated(listOf(AudioTrackUIState(0, "Track 1", "", isFallbackLabel = true)), 0)`; `onAction(PlayerAction.SelectSubtitle(1))`; verify `saveTrackPreferences(42, "rus", "01. Дубляж (RUS)", any())`
- [x] `selectAudioTrack_fallbackRow_keepsStoredAudioPreference`: same setup, `PlayerAction.SelectAudioTrack(0)`, same verify
- [x] Implement; `selectTrack_savesLangToPrefs` (`:97`) stays green unchanged
- [x] Run the fast gate and `PlayerVMTrackSelectionTest`, `AudioTrackPreferenceResolverTest`; commit

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.ui.feature.player.vm.PlayerVMTrackSelectionTest'` passes with both new tests.

---

### Task 5: Full gate and device checks

**Covers:** AC-10; AC-4, AC-5, AC-6, AC-7 (device, run by `qa-device` in verification)
**Files:** none planned; a detekt or lint finding is fixed in the file it names and committed in one commit.

**Consumes:** Tasks 1-4.
**Produces:** a green full gate; device evidence M1-M6 of `test-design.md#manual-checks` on the box `192.168.1.110:5555` (`com.kino.puber.stage` only, worktree dev APK via `tools/android-apk-install-preserve`, lease held).

- [x] Run the full gate from Global constraints
- [x] Record in the Implementation record whether the `hls4` URL differs between qualities if seen in the debug overlay (R6) - the box run itself belongs to `qa-device`

**Done when:** the full gate exits 0 on the last commit.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** see `git log 0823528b..`: Task 1 hls4-first with cap, Task 2 controller pin, Task 3 fallback audio label, Task 4 stored audio preference, one `LargeClass` suppress and this record (Task 5).

**Red run** (P0 `red` tests on the unchanged code):

- red: `PlayerInteractorTest.selectStreamCandidates_manual_prefersSelectedQualityHls4` - AssertionFailedError at :334 (order still `hls` first)
- red: `VideoCapParametersTest.applyVideoCap_cap_setsMaxSizeAndForcesHighestBitrate` - expected 1280 but was 2147483647 (stub extension)
- red: `VideoCapParametersTest.applyVideoCap_null_clearsEarlierCap` - expected 2147483647 but was 1280 (stub)
- red: `VideoCapParametersTest.applyVideoCap_keepsUnrelatedParameters` - expected 720 but was 2147483647 (stub)
- red: `PlaybackControllerNetworkTracksTest.hlsVideoCap_switchStreamReplacesCap` - "720 variant selected after switching to the 1280x720 cap: condition stayed false" (no cap applied)
- red: `PlaybackControllerNetworkTracksTest.hlsVideoCap_belowEveryVariant_stillSelectsVideo` - green with no cap at all (ABR starts at 360); red once the cap was wired with `setExceedVideoConstraintsIfNecessary(false)`: "a video variant is selected although the cap excludes all: condition stayed false"; green after flipping it to `true`
- red: `PlayerAudioTracksTest.buildAudioRows_unlabelledGroup_usesFallbackString` - expected string_..._1 but was "Track 1" (stub with the old rule)
- red: `PlayerAudioTracksTest.buildAudioRows_labelThenLanguage_keepsManifestName` - isFallbackLabel expected true but was false (stub)
- red: `PlayerVMTrackSelectionTest.selectSubtitle_whileAudioRowIsFallback_keepsStoredAudioPreference` and `selectAudioTrack_fallbackRow_keepsStoredAudioPreference` - saveTrackPreferences saw null language and "Track 1" instead of "rus" / stored label
- P1/P2 rows of Task 1 (`manualWithSurround`, `manualFileWithoutWidth`, `manualFileWithoutHeight`, `manualIndexOutOfRange`) were written together with the implementation, not run on the base (types did not exist); the P0 row covers the order and cap.

Itest on the emulator: 11 tests green, result copied to `.flow/runs/manual-quality-hls4/itest-1.xml`; the 640x360 variant assumption of AC-3 held. Lease released, emulator stopped.

**Deviations from the plan:**

- `VideoCapParametersTest` stubs `TextUtils` (as `PlayerTextTracksTest` does) because `setPreferredAudioLanguage` normalises the language.
- `@Suppress("LargeClass")` on `PlayerInteractorTest` (831 lines): detekt `LargeClass` failed the full gate; same suppression as five other test classes.
- R6 (is the `hls4` URL per quality): not seen, no debug overlay in this run; left to `qa-device`.

**Files touched outside the plan:** none.

**For the reviewers:** `PlaybackController.kt` `prepare` now exceeds video constraints when necessary (AC-3); `switchStream` rebuilds the saved parameters with the new cap so an Auto switch lifts a manual pin. `PlayerVM.saveTrackPreferences` re-saves the stored audio preference on a fallback row.

**Fix round 1** (triage-1 #1-#4):

- #1 (F1): both cap tests now assert the player's parameters after prepare (`maxVideoSize`, force-highest, viewport; `(320, 180)` in the AC-3 test). Local experiment with `.applyVideoCap` removed from `prepare`: `hlsVideoCap_switchStreamReplacesCap` ("prepare applied the 640x360 cap without a viewport trim: condition stayed false") and `hlsVideoCap_belowEveryVariant_stillSelectsVideo` ("prepare applied the 320x180 cap: condition stayed false") fail; reverted, not committed.
- #2 (F2): the Auto guard's file has `w = 1920, h = 1080`.
- #3 (F3): no fixture has an audio track without label or language (every HLS rendition has NAME, the progressive MP4 reports language `und`, which `buildAudioRows` uses as the name). `unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback` prepares `/media/hls/audio_english.m3u8` directly as a media playlist: no EXT-X-MEDIA, so Media3 reports no label or language. It asserts the `Callback.onTracksUpdated` row has `isFallbackLabel` and the `R.string.player_audio_track_fallback` label. No binary fixture added.
- #4 (codex): `applyVideoCap(cap, context)` clears the viewport constraint when a cap is set and restores `setViewportSizeToPhysicalDisplaySize(context, true)` on Auto. Red: `VideoCapParametersTest.applyVideoCap_cap_clearsTheDisplayViewportConstraint` failed on the line removed. The emulator's default viewport is already unbounded, so the instrumented test first sets a 100x100 viewport on the player, then expects the cap switch to clear it; after the Auto switch it expects the `DefaultTrackSelector.Parameters.Builder(context)` default. The Auto restore is only discriminating on a display with a finite default. The JVM null-path test uses a relaxed mock `Context`.
- Itest x3 on the emulator: 12 tests, 0 failures each (`.flow/runs/manual-quality-hls4/itest-fix1-1.xml` to `-3.xml`). Lease released, emulator stopped.

**Blocked:**

## Readiness

Verdict: **READY**

Inputs: spec `docs/superpowers/specs/2026-11-27-manual-quality-hls4.md` (a0243366), plan `docs/superpowers/plans/2026-11-27-manual-quality-hls4.md` (9a0c9501), `test-design.md`, `state.md`. Citations checked against `git show 0823528b:<path>`.

### AC coverage

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | 1 | `selectStreamCandidates_manual_prefersSelectedQualityHls4` (red), `..._auto_returnsEveryProtocolInFallbackOrder` (guard), P1/P2 cap rows |
| AC-2 | 2 | `VideoCapParametersTest` (3), `hlsVideoCap_switchStreamReplacesCap` |
| AC-3 | 2 | `hlsVideoCap_belowEveryVariant_stillSelectsVideo` |
| AC-4 | 5 (qa-device) | M1 |
| AC-5 | 5 (qa-device) | M2 |
| AC-6 | 5 (qa-device) | M3 |
| AC-7 | 5 (qa-device) | M4 |
| AC-8 | 3 | `buildAudioRows_unlabelledGroup_usesFallbackString`, `buildAudioRows_labelThenLanguage_keepsManifestName` |
| AC-9 | 4 | `selectSubtitle_whileAudioRowIsFallback_keepsStoredAudioPreference`, `selectAudioTrack_fallbackRow_keepsStoredAudioPreference`, `selectTrack_savesLangToPrefs` (guard) |
| AC-10 | 5 | check.full |

### Citations sampled (base 0823528b)

| Citation | Resolves to |
|----------|-------------|
| `PlayerInteractor.kt:26` | `StreamCandidate(url, type)` |
| `PlayerInteractor.kt:242-251` | `manualStreamCandidates`, `hls` before `hls4` |
| `PlayerInteractor.kt:227-230` | `filter` / `copy(url=withSurroundAudioPreference)` / `distinctBy` |
| `PlayerInteractor.kt:337,341` | `getPreferredAudioLang`, `getPreferredAudioLabel` |
| `PlaybackController.kt:161` | `private val context: Context` |
| `PlaybackController.kt:402,425-432,426` | `prepare`; selector parameters; `setExceedVideoConstraintsIfNecessary(false)` |
| `PlaybackController.kt:526,530,543` | `switchStream`; `savedTrackParams` read and restored |
| `PlaybackController.kt:630` | `disableAc3AndRetry` |
| `PlaybackController.kt:864,871-879,873` | `notifyTracksUpdated`; `"Track ${index + 1}"` |
| `PlayerVM.kt:576` | `reportedAudioTracks.ifEmpty { audioTracks }` |
| `PlayerVM.kt:907,917,1955,1967-1968` | `applyAudioTrackSelection`, `applySubtitleSelection`, `saveTrackPreferences` and its audio args |
| `PlayerUIModels.kt:20,59` | `AudioTrackUIState`, `QualityUIState` |
| `Models.kt:579,590` | `VideoFile` (`w`, `h`), `VideoUrl` (`hls4`) |
| `PlaybackControllerHarness.kt:190,201`; `PlaybackControllerReads.kt:25`; `PlaybackControllerRoutes.kt:34` | `stream`, `prepare`, `selectedAudioLanguage`, `commonHlsRoutes` |
| `PlayerInteractorTest.kt:294,316,346`; `PlayerVMTestFixture.kt:77,107-108,193`; `PlayerVMTrackSelectionTest.kt:97`; `PlayerTextTracksTest.kt:27-36` | the named tests, slot, stubs and `TextUtils` stub |
| `values/player.xml:47`, `values-en/strings.xml:301` | `player_subtitle_unknown` in both locales |

The fixture `hls/master.m3u8` holds 640x360 and 1280x720 variants, as the AC-2/AC-3 tests assume. `PlaybackControl.Callback.onTracksUpdated` has a defaulted third argument, so the two-argument call in Task 4 compiles.

### Order and build

Task order follows dependencies: 1 -> 2 (`VideoSizeCap`), 3 -> 4 (`isFallbackLabel`), 5 last. The new properties default (`videoCap = null`, `isFallbackLabel = false`), so existing positional constructions keep compiling after each task. Both locale folders (`values`, `values-en`) get the new string.

### Test design P0

Automated P0 rows are all marked `red` or `guard`, name the defect, and name the test in backticks. M1-M3 and AC-10 are P0 device and command checks and carry no red/guard mark because they are not automated tests.

### Scope and questions

The plan stays inside Out of scope (Q3, D1, D2, `warmUpNext`). Blocking Q1 and Q2 are answered in `state.md`. Instrumented runs use the emulator under the run's recorded decision in `state.md`.

### Non-blocking notes

1. The spec's root-cause table cites `PlayerVM.kt:558` for `reportedAudioTracks.ifEmpty`. Line 558 is the `onTracksUpdated` header; the expression is at `:576`, and the Design section cites `:576` correctly.
2. AC-3 expects the 640x360 variant when the cap excludes every variant. That relies on Media3 preferring the lowest-quality track once constraints are exceeded. If the emulator run picks 720, the test or the design must change; do not weaken the assertion.

