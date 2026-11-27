# Readiness 1: manual-quality-hls4

Verdict: **READY**

Inputs: spec `docs/superpowers/specs/2026-11-27-manual-quality-hls4.md` (a0243366), plan `docs/superpowers/plans/2026-11-27-manual-quality-hls4.md` (9a0c9501), `test-design.md`, `state.md`. Citations checked against `git show 0823528b:<path>`.

## AC coverage

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

## Citations sampled (base 0823528b)

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

## Order and build

Task order follows dependencies: 1 -> 2 (`VideoSizeCap`), 3 -> 4 (`isFallbackLabel`), 5 last. The new properties default (`videoCap = null`, `isFallbackLabel = false`), so existing positional constructions keep compiling after each task. Both locale folders (`values`, `values-en`) get the new string.

## Test design P0

Automated P0 rows are all marked `red` or `guard`, name the defect, and name the test in backticks. M1-M3 and AC-10 are P0 device and command checks and carry no red/guard mark because they are not automated tests.

## Scope and questions

The plan stays inside Out of scope (Q3, D1, D2, `warmUpNext`). Blocking Q1 and Q2 are answered in `state.md`. Instrumented runs use the emulator under the run's recorded decision in `state.md`.

## Non-blocking notes

1. The spec's root-cause table cites `PlayerVM.kt:558` for `reportedAudioTracks.ifEmpty`. Line 558 is the `onTracksUpdated` header; the expression is at `:576`, and the Design section cites `:576` correctly.
2. AC-3 expects the 640x360 variant when the cap excludes every variant. That relies on Media3 preferring the lowest-quality track once constraints are exceeded. If the emulator run picks 720, the test or the design must change; do not weaken the assertion.
